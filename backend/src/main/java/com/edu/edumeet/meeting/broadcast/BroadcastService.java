package com.edu.edumeet.meeting.broadcast;

import com.edu.edumeet.meeting.config.BroadcastProperties;
import com.edu.edumeet.meeting.domain.BroadcastCodecPlan;
import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.domain.SessionType;
import com.edu.edumeet.meeting.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 자체 HLS 송출. LiveKit egress 를 대신한다. (#123)
 *
 * <p><b>왜 egress 를 걷어냈나.</b> egress 의 {@code RoomComposite} 가 CPU 4를 요구하는 이유는
 * 여러 참가자 화면을 헤드리스 Chrome 으로 렌더링해 <b>합성</b>하기 때문이다.
 * 그런데 <b>방송 모드는 발표자 한 명만 나간다. 합성할 것이 없다.</b>
 * 합성이 필요 없는데 합성기를 쓰고 있었고, 그 대가로 2코어에서 비디오 방송을 포기하고 있었다.
 *
 * <p>발표자 브라우저가 이미 인코딩한 조각을 받아 <b>컨테이너만 바꾸면</b> 되고,
 * 그 경우 CPU 는 거의 들지 않는다. 다만 브라우저가 H264 를 안 주면 다시 인코딩해야 한다.
 * 어느 쪽인지는 {@link BroadcastCodecPlan} 이 정하고 <b>로그에 남긴다.</b>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BroadcastService {

    private final BroadcastProperties properties;
    private final MeetingRepository meetingRepository;

    private final MeterRegistry meterRegistry;
    private final com.edu.edumeet.chat.metrics.ChatMetrics chatMetrics;
    private final Map<Long, BroadcastSession> sessions = new ConcurrentHashMap<>();

    /**
     * 기동 때 정리한 유령 방송 수. (#168)
     *
     * <p>0 이 아니면 <b>직전 배포가 방송을 끊었다</b>는 뜻이다.
     * 로그로만 남기면 배포 로그를 뒤져야 알 수 있어 지표로도 낸다.
     */
    private Counter staleReconciled;

    @PostConstruct
    void registerMetrics() {
        this.staleReconciled = Counter.builder("broadcast.stale.reconciled")
                .description("기동 때 정리한 유령 방송 수. 0 이 아니면 직전 배포가 방송을 끊었다")
                .register(meterRegistry);
        io.micrometer.core.instrument.Gauge
                .builder("broadcast.active", sessions, Map::size)
                .description("지금 송출 중인 방송 수. 상한은 maxConcurrent 다")
                .register(meterRegistry);

        // ★ 아무도 안 듣는데 인코딩 중인 방. (#168)
        //
        //   2코어에서 방송 하나가 CPU 를 쓴다. 시청자가 0인데 계속 돌면
        //   그 CPU 가 통째로 낭비되고, 다른 방송이 상한(maxConcurrent)에 막힌다.
        //
        //   시청자를 어떻게 세나 - HLS 세그먼트는 nginx 가 직접 내주므로
        //   앱이 그 요청을 못 본다. nginx 는 호스트에서 돌아 로그 수집기도 안 닿는다.
        //   대신 시청 화면이 어차피 STOMP 를 열고 방을 구독한다.
        //   그 구독자 수가 시청자 수의 근사다.
        //
        //   ★ 자동으로 멈추지는 않는다. 발표자가 의도해서 켠 것이고,
        //     아직 아무도 안 들어왔을 뿐일 수 있다. 끄는 판단은 사람이 한다.
        io.micrometer.core.instrument.Gauge
                .builder("broadcast.unwatched", this, BroadcastService::unwatchedCount)
                .description("송출 중인데 구독자가 0인 방 수. CPU 를 아무도 안 보는 데 쓰고 있다")
                .register(meterRegistry);
    }

    /**
     * 송출 중인데 아무도 안 듣는 방 수. (#168)
     *
     * <p>시청 화면은 자막과 채팅을 받으려고 STOMP 로 방을 구독한다.
     * 그 구독자가 0이면 <b>세그먼트를 만들어 두고 아무도 안 가져가는 상태</b>다.
     *
     * <p><b>근사다.</b> 브라우저를 닫지 않고 탭만 백그라운드로 둔 사람도 구독자로 세고,
     * 반대로 채팅을 끄고 영상만 보는 클라이언트가 생기면 못 센다.
     * 지금 프론트는 접속하면 무조건 둘 다 구독하므로 그 경우는 없다.
     */
    public int unwatchedCount() {
        int unwatched = 0;
        for (Long meetingId : sessions.keySet()) {
            if (chatMetrics.subscriberCount("/topic/rooms/" + meetingId) == 0) {
                unwatched++;
            }
        }
        return unwatched;
    }

    /**
     * 방송을 시작한다.
     *
     * @param mimeType MediaRecorder 가 <b>실제로 고른</b> 값. 클라이언트가 원한 값이 아니다
     * @return 시청자에게 줄 플레이리스트 주소
     */
    @Transactional
    public String start(String email, Long meetingId, String mimeType) {
        Meeting meeting = requireHost(email, meetingId);
        SessionType type = meeting.getSessionType();

        if (type == SessionType.INTERACTIVE) {
            throw new IllegalArgumentException(
                    "화상강의는 HLS 로 내보내지 않습니다. 1초 미만 지연이 목적인데 HLS 는 세그먼트 길이만큼 늦습니다.");
        }
        if (sessions.containsKey(meetingId)) {
            return sessions.get(meetingId).getPlaylistUrl();
        }

        BroadcastCodecPlan plan = BroadcastCodecPlan.of(mimeType, type);
        admit(plan);

        Path dir = Path.of(properties.getOutputDir(), "meeting-" + meetingId);
        prepareDirectory(dir);

        List<String> cmd = FfmpegCommand.build(properties, plan, dir.toString());
        log.info("방송 시작 - meetingId={}, type={}, 코덱={}, mimeType={}",
                meetingId, type, plan.describe(), mimeType);

        Process process = spawn(cmd, meetingId);
        String playlistUrl = "%s/meeting-%d/live.m3u8".formatted(trimTrailingSlash(properties.getPublicBaseUrl()), meetingId);

        sessions.put(meetingId, new BroadcastSession(
                meetingId, process, plan, playlistUrl, properties.getReorderWindow()));

        meeting.startBroadcast("self-" + meetingId, playlistUrl);
        return playlistUrl;
    }

    /**
     * 청크 하나를 받는다.
     *
     * @return 받아들였으면 true. false 면 호출자가 429 로 돌려준다
     */
    public boolean acceptChunk(Long meetingId, long seq, byte[] data) {
        BroadcastSession session = sessions.get(meetingId);
        if (session == null || !session.isAlive()) {
            throw new IllegalStateException("진행 중인 방송이 없습니다: " + meetingId);
        }
        if (data.length > properties.getMaxChunkBytes()) {
            throw new IllegalArgumentException(
                    "청크가 너무 큽니다: %d 바이트 (상한 %d)".formatted(data.length, properties.getMaxChunkBytes()));
        }
        return session.submit(seq, data);
    }

    @Transactional
    public void stop(String email, Long meetingId) {
        Meeting meeting = requireHost(email, meetingId);
        closeSession(meetingId);
        meeting.stopBroadcast();
    }

    /**
     * 유령 방송을 걷어낸다.
     *
     * <p><b>종료 요청은 오지 않을 수 있다고 가정한다.</b> 발표자가 탭을 닫거나 네트워크가
     * 끊기면 stop 이 안 온다. 그대로 두면 ffmpeg 가 영원히 남아 2코어를 갉아먹는다.
     */
    @Scheduled(fixedDelayString = "${edumeet.broadcast.reap-interval-ms:10000}")
    public void reapIdleSessions() {
        long limit = properties.getIdleTimeout().toMillis();
        sessions.forEach((meetingId, session) -> {
            if (!session.isAlive()) {
                log.warn("방송 프로세스가 죽어 있다 - meetingId={}. 정리한다", meetingId);
                closeSession(meetingId);
                return;
            }
            if (session.idleMillis() > limit) {
                log.warn("방송이 {}ms 동안 조용하다 - meetingId={}. 발표자가 사라진 것으로 본다",
                        session.idleMillis(), meetingId);
                closeSession(meetingId);
            }
        });
    }

    /**
     * 기동하면서 DB 에 남은 유령 방송을 정리한다. (#168)
     *
     * <h3>왜 필요한가</h3>
     * 송출 세션은 {@code sessions} 맵이라 <b>메모리에만 있다.</b> 앱이 재시작되면
     * ffmpeg 는 같이 죽는데 DB 의 {@code broadcastSessionId} 는 그대로 남는다.
     *
     * <p><b>배포할 때마다 난다.</b> 그런데 증상이 조용하다 —
     * 발표자 화면은 계속 <i>"방송 중"</i> 이고, 시청자는 세그먼트가 안 늘어난다.
     * 에러가 한 줄도 안 나서 <b>아무도 이상하다고 느끼지 않는다.</b>
     *
     * <p>{@link #reapIdleSessions()} 는 이걸 못 잡는다. 그 수거기는 <b>메모리에 있는
     * 세션</b>을 훑는데, 재시작 뒤에는 그 맵이 비어 있다.
     * <b>훑을 것이 없으니 아무것도 안 한다.</b>
     *
     * <h3>왜 되살리지 않고 정리만 하나</h3>
     * 되살릴 수가 없다. 송출은 <b>발표자 브라우저가 조각을 밀어 넣어야</b> 이어진다.
     * 서버 혼자 다시 시작할 수 있는 것이 아니다. 그러니 <b>상태를 사실에 맞추는 것</b>이
     * 할 수 있는 전부다. 발표자는 다시 시작 버튼을 눌러야 한다.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void reconcileOnStartup() {
        List<Meeting> stale = meetingRepository.findAllByBroadcastSessionIdIsNotNull();
        if (stale.isEmpty()) return;

        for (Meeting meeting : stale) {
            log.warn("재시작 전에 방송 중이던 회의를 정리한다 - meetingId={}. "
                            + "송출 프로세스는 재시작과 함께 죽었고 되살릴 수 없다",
                    meeting.getId());
            meeting.stopBroadcast();
        }
        staleReconciled.increment(stale.size());
    }

    /** 지금 돌고 있는 방송 수. 측정과 시험에서 본다. */
    public int activeCount() {
        return sessions.size();
    }

    public BroadcastSession sessionOf(Long meetingId) {
        return sessions.get(meetingId);
    }

    private void closeSession(Long meetingId) {
        BroadcastSession session = sessions.remove(meetingId);
        if (session != null) {
            session.close();
        }
    }

    /**
     * 받아들일지 정한다.
     *
     * <p>상한이 없으면 2코어에서 방송 몇 개가 겹치는 순간 전체가 멈춘다.
     * <b>거부는 아프지만 전부 죽는 것보다 낫다.</b>
     */
    private void admit(BroadcastCodecPlan plan) {
        int active = sessions.size();
        if (active >= properties.getMaxConcurrent()) {
            throw new IllegalStateException(
                    "동시 방송 상한(%d)에 걸렸습니다. 지금 %d 개가 돌고 있습니다."
                            .formatted(properties.getMaxConcurrent(), active));
        }
        if (plan.transcodesVideo() && active >= 1) {
            // 재인코딩은 코어 하나를 통째로 먹는다. 2코어에서 두 개가 겹치면
            // 인코딩이 재생 속도를 못 따라가 지연이 계속 벌어진다.
            throw new IllegalStateException(
                    "비디오 재인코딩이 필요한 방송은 한 번에 하나만 받습니다. "
                            + "브라우저가 H264 를 주면 이 제한이 없습니다.");
        }
    }

    private void prepareDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
            // 이전 방송의 세그먼트가 남아 있으면 플레이어가 옛 조각을 재생한다.
            try (var stream = Files.list(dir)) {
                stream.forEach(p -> p.toFile().delete());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("방송 출력 디렉터리를 준비하지 못했습니다: " + dir, e);
        }
    }

    private Process spawn(List<String> cmd, Long meetingId) {
        try {
            // stderr 를 상속하지 않는다. 전용 스레드가 읽어야 파이프가 안 막힌다.
            return new ProcessBuilder(cmd).redirectErrorStream(false).start();
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "ffmpeg 를 시작하지 못했습니다 - meetingId=" + meetingId
                            + ". 경로: " + properties.getFfmpegPath(), e);
        }
    }

    private Meeting requireHost(String email, Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다: " + meetingId));
        String ownerEmail = meeting.getClassRoom().getMember().getEmail();
        if (!ownerEmail.equals(email)) {
            throw new AccessDeniedException("방송 송출은 클래스 생성자만 제어할 수 있습니다.");
        }
        return meeting;
    }

    private static String trimTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
