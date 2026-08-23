package com.edu.edumeet.unit.meeting;

import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.domain.SessionType;
import com.edu.edumeet.meeting.domain.HlsEgressPlan;
import com.edu.edumeet.meeting.service.HlsEgressPlanner;
import com.edu.edumeet.meeting.config.HlsProperties;
import livekit.LivekitEgress.SegmentedFileOutput;
import livekit.LivekitEgress.SegmentedFileProtocol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HLS 요청의 함정을 고정한다. (#75)
 *
 * <p><b>실제 egress 인스턴스 없이 도는 테스트다.</b>
 * egress 를 띄우려면 {@code --cap-add=SYS_ADMIN} · Chrome · Xvfb · Redis · 4코어가 필요한데
 * 이 노트북에도 우리 서버에도 그 조합이 없다.
 *
 * <p>그래도 검증할 가치가 있는 이유 — <b>HLS 의 실패는 대부분 요청에서 나고, 조용하다.</b>
 * 잘못 만든 요청은 에러를 내지 않고 이상한 방송을 만든다.
 * 실행해서 눈으로 확인할 수 없는 종류의 버그라, 오히려 요청을 값으로 두고 고정해야 한다.
 *
 * <p><b>이 테스트가 증명하지 않는 것</b>: 실제로 재생되는가, 지연이 몇 초인가.
 * 그건 egress 인스턴스를 붙인 뒤 실측해야 한다.
 */
@DisplayName("HLS 요청 만들기")
class HlsEgressPlannerTest {

    private static final long MEETING_ID = 42L;

    private HlsEgressPlanner planner(HlsProperties properties, String endpoint, String bucket) {
        return new HlsEgressPlanner(properties, "ak", "sk", "auto", endpoint, bucket);
    }

    private HlsProperties defaults() {
        return new HlsProperties();   // enabled=false, segmentDuration=2, prefix="hls"
    }

    private Meeting meeting(SessionType type) {
        Meeting meeting = Meeting.builder()
                .title("세션").description("-")
                .sessionType(type).startTime(LocalDateTime.now()).build();
        ReflectionTestUtils.setField(meeting, "id", MEETING_ID);
        return meeting;
    }

    private SegmentedFileOutput outputFor(SessionType type) {
        return planner(defaults(), "", "").plan(meeting(type)).output();
    }

    @Nested
    @DisplayName("조용히 깨지는 것들")
    class SilentFailures {

        @Test
        @DisplayName("★ 라이브 플레이리스트를 비워 두면 라이브 방송에 VOD 플레이리스트가 나간다")
        void live_playlist_must_be_set() {
            SegmentedFileOutput output = outputFor(SessionType.BROADCAST);

            assertThat(output.getLivePlaylistName())
                    .as("""
                        비어 있으면 LiveKit 은 라이브 플레이리스트를 아예 만들지 않는다.
                        에러가 아니라서 더 나쁘다 - 재생은 되는데 방송 시작점부터 재생되고,
                        플레이리스트가 방송 내내 무한히 커진다.""")
                    .isNotBlank()
                    .endsWith("live.m3u8");
        }

        @Test
        @DisplayName("★ 두 플레이리스트는 같은 디렉터리여야 한다 - 다르면 LiveKit 이 거부한다")
        void playlists_must_share_a_directory() {
            SegmentedFileOutput output = outputFor(SessionType.BROADCAST);

            String liveDir = directoryOf(output.getLivePlaylistName());
            String vodDir = directoryOf(output.getPlaylistName());

            assertThat(liveDir)
                    .as("LiveKit: ErrInvalidInput(\"live_playlist_name must be in same directory\")")
                    .isEqualTo(vodDir);
            assertThat(directoryOf(output.getFilenamePrefix()))
                    .as("세그먼트도 같은 곳에 놓여야 상대 경로 참조가 맞는다")
                    .isEqualTo(liveDir);
        }

        @Test
        @DisplayName("★ 세그먼트 길이를 LiveKit 기본값 4초로 두지 않는다")
        void segment_duration_is_not_the_default() {
            SegmentedFileOutput output = outputFor(SessionType.BROADCAST);

            assertThat(output.getSegmentDuration())
                    .as("""
                        HLS 지연은 대략 세그먼트 길이 x 플레이어 버퍼 개수(보통 3)다.
                        4초를 그대로 두면 12초에서 출발한다.""")
                    .isEqualTo(2)
                    .isNotEqualTo(4);
        }

        @Test
        @DisplayName("HLS 프로토콜을 명시한다 - 기본값은 HLS 가 아니다")
        void protocol_is_explicitly_hls() {
            assertThat(outputFor(SessionType.BROADCAST).getProtocol())
                    .isEqualTo(SegmentedFileProtocol.HLS_PROTOCOL)
                    .isNotEqualTo(SegmentedFileProtocol.DEFAULT_SEGMENTED_FILE_PROTOCOL);
        }

        private String directoryOf(String path) {
            return path.substring(0, path.lastIndexOf('/'));
        }
    }

    @Nested
    @DisplayName("세션 형태별 판단")
    class PerSessionType {

        @Test
        @DisplayName("★ 화상강의는 거부한다 - 못 해서가 아니라 하면 안 되기 때문이다")
        void interactive_is_rejected() {
            assertThatThrownBy(() -> outputFor(SessionType.INTERACTIVE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("HLS");

            assertThat(planner(defaults(), "", "").supports(SessionType.INTERACTIVE)).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = SessionType.class, names = {"BROADCAST", "AUDIO_BROADCAST"})
        @DisplayName("방송 두 형태는 내보낼 수 있다")
        void broadcasts_are_supported(SessionType type) {
            assertThat(planner(defaults(), "", "").supports(type)).isTrue();
        }

        @Test
        @DisplayName("★ 오디오 방송만 audioOnly 로 나간다")
        void only_audio_broadcast_is_audio_only() {
            assertThat(planner(defaults(), "", "").plan(meeting(SessionType.AUDIO_BROADCAST)).audioOnly())
                    .isTrue();
            assertThat(planner(defaults(), "", "").plan(meeting(SessionType.BROADCAST)).audioOnly())
                    .isFalse();
        }
    }

    /**
     * <b>이 프로젝트에서 HLS 를 하려면 반드시 알아야 하는 숫자다.</b>
     *
     * <p>LiveKit egress 소스에서 확인했다.
     * <pre>
     *   pkg/config/service.go   roomCompositeCpuCost = 4, audioRoomCompositeCpuCost = 1
     *   pkg/stats/monitor.go    if AudioOnly { cpu = Audio... } else { cpu = Room... }
     *                           accept := available &gt;= required  // 아니면 ErrNotEnoughCPU
     * </pre>
     */
    @Nested
    @DisplayName("우리 서버(2 OCPU)에서 무엇이 되는가")
    class CpuBudget {

        private static final double OUR_SERVER_CORES = 2;

        @Test
        @DisplayName("★ 비디오 방송 HLS 는 우리 서버에서 거부된다 - 4코어가 필요하다")
        void video_broadcast_does_not_fit() {
            HlsEgressPlan plan = planner(defaults(), "", "").plan(meeting(SessionType.BROADCAST));

            assertThat(plan.estimatedCpuCost()).isEqualTo(4);
            assertThat(plan.fitsOn(OUR_SERVER_CORES))
                    .as("2 >= 4 이 거짓이라 ErrNotEnoughCPU 가 난다")
                    .isFalse();
        }

        @Test
        @DisplayName("★ 오디오 방송 HLS 는 우리 서버에서 돈다 - 헤드리스 Chrome 을 건너뛴다")
        void audio_broadcast_fits() {
            HlsEgressPlan plan = planner(defaults(), "", "").plan(meeting(SessionType.AUDIO_BROADCAST));

            assertThat(plan.estimatedCpuCost())
                    .as("싸진 게 아니라 파이프라인이 다르다. Chromium · Xvfb · 화면 합성이 통째로 빠진다")
                    .isEqualTo(1);
            assertThat(plan.fitsOn(OUR_SERVER_CORES)).isTrue();
        }
    }

    @Nested
    @DisplayName("R2 로 올릴 때")
    class R2Storage {

        private static final String R2 = "https://acc.r2.cloudflarestorage.com";

        @Test
        @DisplayName("★ forcePathStyle 을 켜야 한다 - R2 는 가상 호스트 주소를 안 받는다")
        void force_path_style_is_on() {
            SegmentedFileOutput output =
                    planner(defaults(), R2, "edumeet").plan(meeting(SessionType.BROADCAST)).output();

            assertThat(output.getS3().getForcePathStyle())
                    .as("끄면 egress 가 업로드 단계에서 실패한다. S3Presigner 에서 이미 겪은 문제다")
                    .isTrue();
            assertThat(output.getS3().getEndpoint()).isEqualTo(R2);
            assertThat(output.getS3().getBucket()).isEqualTo("edumeet");
        }

        @Test
        @DisplayName("버킷이 자리표시자면 스토리지를 붙이지 않는다 - 로컬 디스크로 떨어진다")
        void placeholder_bucket_means_no_storage() {
            SegmentedFileOutput output =
                    planner(defaults(), "", "not-configured").plan(meeting(SessionType.BROADCAST)).output();

            assertThat(output.hasS3())
                    .as("자리표시자를 그대로 보내면 egress 가 인증 실패로 죽는다")
                    .isFalse();
        }

        @Test
        @DisplayName("플레이어에게 줄 주소는 라이브 플레이리스트를 가리킨다")
        void public_url_points_at_live_playlist() {
            HlsProperties props = defaults();
            props.setPublicBaseUrl("https://cdn.example.com/");

            assertThat(planner(props, R2, "edumeet").livePlaylistUrl(MEETING_ID))
                    .isEqualTo("https://cdn.example.com/hls/meeting-42/live.m3u8");
        }
    }
}
