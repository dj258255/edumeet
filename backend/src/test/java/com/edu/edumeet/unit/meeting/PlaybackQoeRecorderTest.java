package com.edu.edumeet.unit.meeting;

import com.edu.edumeet.meeting.broadcast.qoe.PlaybackQoeRecorder;
import com.edu.edumeet.meeting.broadcast.qoe.PlaybackQoeReport;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("시청 품질 보고 기록기")
class PlaybackQoeRecorderTest {

    @Test
    @DisplayName("유효한 보고는 초 단위로 나눠 지표에 쌓인다")
    void valid_report_accumulates_metrics() {
        MeterRegistry registry = new SimpleMeterRegistry();
        PlaybackQoeRecorder recorder = new PlaybackQoeRecorder(registry);

        boolean accepted = recorder.record(report(30_000, 27_000, 3_000, 2, 1_200L, 0));

        assertThat(accepted).isTrue();
        assertThat(counter(registry, "playback.qoe.reports")).isEqualTo(1.0);
        assertThat(counter(registry, "playback.playing.seconds")).isEqualTo(27.0);
        assertThat(counter(registry, "playback.stall.seconds")).isEqualTo(3.0);
        assertThat(counter(registry, "playback.stall.count")).isEqualTo(2.0);
        assertThat(registry.find("playback.startup").summary().totalAmount()).isEqualTo(1.2);
        assertThat(counter(registry, "playback.qoe.rejected")).isZero();
    }

    @Test
    @DisplayName("경계값 바로 안은 통과한다")
    void accepts_boundary_values() {
        assertAccepted(report(120_000, 120_000, 0, 0, null, 0));     // 간격 상한 (2분)
        assertAccepted(report(30_000, 34_000, 0, 0, null, 0));       // 재생 = 간격 × 1.1 + 1초
        assertAccepted(report(30_000, 30_000, 4_000, 0, null, 0));   // 재생+끊김 = 간격 × 1.1 + 1초
        assertAccepted(report(30_000, 30_000, 0, 60, null, 0));      // 끊김 횟수 상한
        assertAccepted(report(30_000, 30_000, 0, 0, null, 1_000));   // 오류 상한
        assertAccepted(report(30_000, 30_000, 0, 0, 120_000L, 0));   // 첫 화면 상한
        assertAccepted(report(30_000, 30_000, 0, 0, null, 0));       // 첫 화면 없음
        assertAccepted(withSessionId("s".repeat(64)));               // 세션 아이디 상한
    }

    @Test
    @DisplayName("★ 둘 다 Long.MAX_VALUE 여도 합이 넘쳐 통과하지 않는다")
    void rejects_overflowing_sum() {
        assertRejected(report(30_000, Long.MAX_VALUE, Long.MAX_VALUE, 0, null, 0));
    }

    @Test
    @DisplayName("★ 상한을 넘는 보고는 버리고 rejected 만 센다")
    void rejects_out_of_range_reports() {
        assertRejected(report(120_001, 120_000, 0, 0, null, 0));     // 간격 초과
        assertRejected(report(30_000, 34_001, 0, 0, null, 0));       // 재생 = 상한 + 1ms
        assertRejected(report(30_000, 30_000, 4_001, 0, null, 0));   // 재생+끊김 = 상한 + 1ms
        assertRejected(report(30_000, 30_000, 0, 61, null, 0));      // 끊김 횟수 초과
        assertRejected(report(30_000, 30_000, 0, 0, null, 1_001));   // 오류 초과
        assertRejected(report(30_000, 30_000, 0, 0, 120_001L, 0));   // 첫 화면 초과
        assertRejected(report(30_000, 30_000, 0, 0, -1L, 0));        // 첫 화면 음수
        assertRejected(withSessionId("s".repeat(65)));              // 세션 아이디 초과
        assertRejected(withSessionId("  "));                        // 빈 세션 아이디
        assertRejected(withSessionId(null));
        assertRejected(report(-1, 0, 0, 0, null, 0));                // 간격 음수
        assertRejected(report(30_000, -1, 0, 0, null, 0));           // 재생 시간 음수
        assertRejected(report(30_000, 30_000, -1, 0, null, 0));      // 끊김 시간 음수
        assertRejected(report(30_000, 30_000, 0, -1, null, 0));      // 끊김 횟수 음수
        assertRejected(report(30_000, 30_000, 0, 0, null, -1));      // 오류 음수
        assertRejected(new PlaybackQoeReport("s-1", -1, 30_000, 30_000, 0, 0, null, 0, false, false));
    }

    @Test
    @DisplayName("★ playback.* 지표에는 라벨이 없다")
    void playback_metrics_have_no_tags() {
        MeterRegistry registry = new SimpleMeterRegistry();
        new PlaybackQoeRecorder(registry).record(report(30_000, 30_000, 0, 0, 1_000L, 0));

        List<Meter> meters = registry.getMeters().stream()
                .filter(m -> m.getId().getName().startsWith("playback"))
                .toList();

        assertThat(meters).isNotEmpty();
        assertThat(meters).allSatisfy(meter -> assertThat(meter.getId().getTags())
                .as("지표 %s 에 라벨이 붙으면 방송마다 시계열이 늘어난다 (카디널리티)",
                        meter.getId().getName())
                .isEmpty());
    }

    private static PlaybackQoeReport report(long intervalMs, long playingMs, long stallMs,
                                            long stallCount, Long startupMs, int errors) {
        return new PlaybackQoeReport("s-1", 1, intervalMs, playingMs, stallMs,
                stallCount, startupMs, errors, false, false);
    }

    private static PlaybackQoeReport withSessionId(String sessionId) {
        return new PlaybackQoeReport(sessionId, 1, 30_000, 30_000, 0, 0, null, 0, false, false);
    }

    private static void assertAccepted(PlaybackQoeReport report) {
        assertThat(new PlaybackQoeRecorder(new SimpleMeterRegistry()).record(report)).isTrue();
    }

    private static void assertRejected(PlaybackQoeReport report) {
        MeterRegistry registry = new SimpleMeterRegistry();
        PlaybackQoeRecorder recorder = new PlaybackQoeRecorder(registry);

        assertThat(recorder.record(report)).isFalse();
        assertThat(counter(registry, "playback.qoe.rejected")).isEqualTo(1.0);
        assertThat(counter(registry, "playback.qoe.reports")).isZero();
        assertThat(counter(registry, "playback.playing.seconds")).isZero();
        assertThat(counter(registry, "playback.stall.seconds")).isZero();
        assertThat(counter(registry, "playback.stall.count")).isZero();
        assertThat(counter(registry, "playback.errors")).isZero();
        assertThat(registry.find("playback.startup").summary().totalAmount()).isZero();
    }

    private static double counter(MeterRegistry registry, String name) {
        var counter = registry.find(name).counter();
        return counter == null ? 0 : counter.count();
    }
}
