package com.edu.edumeet.meeting.broadcast.qoe;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * 시청 품질 보고를 검증하고 지표·로그로 남긴다. (#197)
 *
 * <h3>왜 라벨이 없나</h3>
 *
 * <p>Prometheus 지표에 {@code meetingId}·사용자·세션 라벨을 붙이면 <b>방송마다 새 시계열</b>이
 * 생긴다. 회의가 쌓일수록 시계열이 무한히 늘어나고(카디널리티), 결국 저장소가 터진다.
 * 그래서 여기서는 <b>라벨 없는 합계</b>만 낸다. "리버퍼율이 몇 %인가" 는 합계로 답하고,
 * "3번 회의는 어땠나" 는 세션 단위 JSON 로그를 Loki 에서 거른다.
 *
 * <h3>왜 MySQL 이 아닌가</h3>
 *
 * <p>30초마다 300명이면 초당 10건이다. 보고 하나가 운영 DB INSERT 가 되면
 * <b>방송 품질을 재려다 서비스의 쓰기 부하를 올린다.</b> 집계는 Prometheus,
 * 세션 상세는 로그(이미 JSON 으로 나가고 meetingId 가 MDC 에 있다, #166)로 나눈다.
 *
 * <h3>왜 검증하나</h3>
 *
 * <p>합계는 <b>클라이언트가 보낸 값</b>을 더한다. 클라이언트가 잘못 세거나 악의로 큰 값을
 * 보내면 합계 전체가 오염된다. 서버가 상한을 걸고, 넘는 보고는 <b>버리고 센다.</b>
 *
 * <h3>왜 거부한 보고는 로그를 안 남기나</h3>
 *
 * <p>넘치기 시작하면 로그가 먼저 시스템을 먹는다. {@code ChatArchiveQueue} 가 같은 이유로
 * 거부를 지표로만 남긴다. 거부는 지표({@code playback.qoe.rejected})로 보고,
 * 한 클라이언트가 합계를 오염시키지 못하게 한다.
 */
@Component
public class PlaybackQoeRecorder {

    private static final Logger log = LoggerFactory.getLogger(PlaybackQoeRecorder.class);

    /**
     * 보고 간격 상한(2분).
     *
     * <p>Chrome 은 오래 가려진(백그라운드) 탭의 타이머를 1분 단위로 묶어 실행한다.
     * 이 서비스에는 <b>탭을 가려 두고 듣는 오디오 방송</b>이 있어, 45초 상한이면
     * 그 시청자의 보고가 전부 거부된다.
     *
     * <p>노트북 절전 등으로 2분을 넘긴 보고는 거부되고 {@code rejected} 로 센다.
     */
    static final long MAX_INTERVAL_MS = 120_000L;
    /** 재생+끊김 시간의 여유. 타이머 오차로 간격을 살짝 넘길 수 있다. */
    static final double DURATION_SLACK = 1.1;
    /** 한 보고 안의 끊김 횟수 상한. 30초에 2초씩 끊겨도 15회다. */
    static final long MAX_STALL_COUNT = 60L;
    static final long MAX_ERRORS = 1_000L;
    /** 첫 화면 시간 상한. 2분을 넘으면 사용자는 이미 떠났다. */
    static final long MAX_STARTUP_MS = 120_000L;
    static final int MAX_SESSION_ID_LENGTH = 64;

    private final Counter reports;
    private final Counter rejected;
    private final Counter playingSeconds;
    private final Counter stallSeconds;
    private final Counter stallCount;
    private final DistributionSummary startup;
    private final Counter errors;

    public PlaybackQoeRecorder(MeterRegistry registry) {
        this.reports = Counter.builder("playback.qoe.reports")
                .description("받아들인 시청 품질 보고 수. 30초마다 한 건이다")
                .register(registry);
        this.rejected = Counter.builder("playback.qoe.rejected")
                .description("검증에서 버린 보고. 클라이언트 하나가 합계를 오염시키지 못하게")
                .register(registry);
        this.playingSeconds = Counter.builder("playback.playing.seconds")
                .description("재생한 시간(초)의 합. 끊김 없이 본 시간(리버퍼율의 분모)이다")
                .register(registry);
        this.stallSeconds = Counter.builder("playback.stall.seconds")
                .description("끊긴 시간(초)의 합. 리버퍼율 = stall.seconds / (playing.seconds + stall.seconds)")
                .register(registry);
        this.stallCount = Counter.builder("playback.stall.count")
                .description("끊김 횟수. 초가 길어도 횟수가 적으면 드물게 오래 멈춘 것이다")
                .register(registry);
        this.errors = Counter.builder("playback.errors")
                .description("재생 오류 수. 클라이언트가 센 값이라 화면에 못 뜬 오류는 안 잡힌다")
                .register(registry);
        this.startup = DistributionSummary.builder("playback.startup")
                .description("첫 화면까지 걸린 시간(초). 플레이어 부착에서 첫 재생까지")
                .publishPercentileHistogram()   // Prometheus 에서 p95 를 구하려면 버킷이 필요하다
                .register(registry);
    }

    /**
     * 보고 한 건을 기록한다.
     *
     * @return 받아들였으면 true. false 면 호출자가 400 으로 돌려준다
     */
    public boolean record(PlaybackQoeReport report) {
        if (!valid(report)) {
            rejected.increment();
            return false;
        }

        reports.increment();
        playingSeconds.increment(report.playingMs() / 1000.0);
        stallSeconds.increment(report.stallMs() / 1000.0);
        stallCount.increment(report.stallCount());
        errors.increment(report.errors());
        if (report.startupMs() != null) {
            startup.record(report.startupMs() / 1000.0);
        }

        log.info("시청 품질 보고",
                kv("sessionId", report.sessionId()),
                kv("seq", report.seq()),
                kv("playingMs", report.playingMs()),
                kv("stallMs", report.stallMs()),
                kv("stallCount", report.stallCount()),
                kv("startupMs", report.startupMs()),
                kv("errors", report.errors()),
                kv("final", report.last()),
                kv("native", report.nativePlayer()));
        return true;
    }

    /** 상한을 넘는 보고를 버린다. 거부는 세기만 하고 로그는 남기지 않는다. */
    static boolean valid(PlaybackQoeReport report) {
        if (report == null) return false;

        String sessionId = report.sessionId();
        if (sessionId == null || sessionId.isBlank() || sessionId.length() > MAX_SESSION_ID_LENGTH) {
            return false;
        }

        if (report.seq() < 0) return false;
        if (report.intervalMs() < 0 || report.playingMs() < 0 || report.stallMs() < 0
                || report.stallCount() < 0 || report.errors() < 0) {
            return false;
        }
        if (report.intervalMs() > MAX_INTERVAL_MS) return false;
        // 값마다 따로 반올림하고 시계 호출 시점이 달라(performance.now vs Date.now),
        // 화면을 바로 떠날 때의 짧은 마지막 보고는 10% 여유가 1ms 보다 작다.
        // 그래서 절대 여유 1초를 더한 값을 상한으로 쓴다.
        long limit = (long) (report.intervalMs() * DURATION_SLACK) + 1_000L;
        // 넘침 방지. 둘을 더하기 전에 각각을 합계와 같은 상한으로 먼저 거른다 -
        // 둘 다 Long.MAX_VALUE 면 합이 음수로 넘쳐 아래 검사를 그냥 통과한다.
        // intervalMs 상한(2분) 덕에 이 상한도 작아서 두 값을 더해도 넘치지 않는다.
        if (report.playingMs() > limit || report.stallMs() > limit) {
            return false;
        }
        if (report.playingMs() + report.stallMs() > limit) {
            return false;
        }
        if (report.stallCount() > MAX_STALL_COUNT) return false;
        if (report.errors() > MAX_ERRORS) return false;
        if (report.startupMs() != null
                && (report.startupMs() < 0 || report.startupMs() > MAX_STARTUP_MS)) {
            return false;
        }
        return true;
    }
}
