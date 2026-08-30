package com.edu.edumeet.unit.meeting;

import com.edu.edumeet.meeting.broadcast.FfmpegCommand;
import com.edu.edumeet.meeting.config.BroadcastProperties;
import com.edu.edumeet.meeting.domain.BroadcastCodecPlan;
import com.edu.edumeet.meeting.domain.SessionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ffmpeg 인자. (#123)
 *
 * <p><b>여기 있는 것들은 빠뜨려도 에러가 안 난다.</b> 방송은 시작되고 재생도 되는데
 * 조용히 잘못 동작한다 — 그래서 시험으로 고정한다.
 */
@DisplayName("ffmpeg 명령 만들기")
class FfmpegCommandTest {

    private BroadcastProperties props;

    @BeforeEach
    void setUp() {
        props = new BroadcastProperties();
        props.setSegmentSeconds(2);
        props.setPlaylistSize(6);
    }

    private List<String> buildFor(String mimeType, SessionType type) {
        return FfmpegCommand.build(props, BroadcastCodecPlan.of(mimeType, type), "/tmp/out");
    }

    private static String flagValue(List<String> cmd, String flag) {
        int i = cmd.indexOf(flag);
        return i < 0 || i + 1 >= cmd.size() ? null : cmd.get(i + 1);
    }

    @Nested
    @DisplayName("조용히 잘못되는 것들")
    class SilentlyWrong {

        @Test
        @DisplayName("★ omit_endlist 가 없으면 라이브가 VOD 로 재생된다 - 방송 시작점부터 튼다")
        void live_playlist_must_omit_endlist() {
            String flags = flagValue(buildFor("video/mp4;codecs=avc1", SessionType.BROADCAST), "-hls_flags");

            assertThat(flags).contains("omit_endlist");
        }

        @Test
        @DisplayName("★ program_date_time 이 없으면 자막을 화면에 맞출 수 없다")
        void manifest_must_carry_wall_clock() {
            String flags = flagValue(buildFor("video/mp4;codecs=avc1", SessionType.BROADCAST), "-hls_flags");
            assertThat(flags)
                    .as("""
                        이게 없으면 플레이어가 아는 것은 "재생 위치 12.3초" 뿐이고
                        그 화면이 몇 시에 촬영된 것인지는 모른다.

                        자막은 WebSocket 으로 1초 안에 오는데 영상은 HLS 라 몇 초 뒤에 온다.
                        그래서 자막이 화면보다 먼저 뜬다. 맞추려면
                        "지금 보여 주는 화면이 몇 시 것인가" 가 필요하다.""")
                    .contains("program_date_time");
        }

        @Test
        @DisplayName("★ delete_segments 가 없으면 디스크가 방송 내내 찬다")
        void old_segments_must_be_deleted() {
            String flags = flagValue(buildFor("video/mp4;codecs=avc1", SessionType.BROADCAST), "-hls_flags");

            assertThat(flags).contains("delete_segments");
        }

        @Test
        @DisplayName("★ 플레이리스트 길이를 0(무한)으로 두지 않는다")
        void playlist_size_is_bounded() {
            String size = flagValue(buildFor("video/mp4;codecs=avc1", SessionType.BROADCAST), "-hls_list_size");

            assertThat(size).isNotEqualTo("0");
            assertThat(Integer.parseInt(size)).isPositive();
        }

        @Test
        @DisplayName("★ 재인코딩할 때 -g 를 안 주면 x264 기본 간격 탓에 세그먼트가 8초씩 나온다")
        void transcoding_forces_keyframe_interval() {
            List<String> cmd = buildFor("video/webm;codecs=vp8", SessionType.BROADCAST);

            // 2초 x 30fps = 60프레임마다 키프레임
            assertThat(flagValue(cmd, "-g")).isEqualTo("60");
            assertThat(flagValue(cmd, "-keyint_min")).isEqualTo("60");
            // 장면 전환 키프레임이 끼면 세그먼트 길이가 들쭉날쭉해진다
            assertThat(flagValue(cmd, "-sc_threshold")).isEqualTo("0");
        }

        @Test
        @DisplayName("★ 리먹싱에는 -g 를 주지 않는다 - copy 는 키프레임을 새로 못 만든다")
        void remuxing_does_not_set_keyframes() {
            List<String> cmd = buildFor("video/mp4;codecs=avc1", SessionType.BROADCAST);

            // 여기에 -g 를 넣으면 ffmpeg 가 무시하거나 경고를 낸다.
            // 그리고 "세그먼트 길이를 통제하고 있다" 는 착각을 만든다 - 실제로는 브라우저가 정한다.
            assertThat(cmd).doesNotContain("-g");
        }
    }

    @Nested
    @DisplayName("세그먼트 길이")
    class SegmentDuration {

        @Test
        @DisplayName("★ 기본값을 그대로 쓰지 않는다 - 지연이 세그먼트 길이에 비례한다")
        void segment_length_is_explicit() {
            assertThat(flagValue(buildFor("video/mp4;codecs=avc1", SessionType.BROADCAST), "-hls_time"))
                    .isEqualTo("2");
        }

        @Test
        @DisplayName("설정을 바꾸면 키프레임 간격도 같이 따라간다")
        void keyframe_interval_follows_segment_length() {
            props.setSegmentSeconds(4);

            assertThat(flagValue(buildFor("video/webm;codecs=vp8", SessionType.BROADCAST), "-g"))
                    .isEqualTo("120");
        }
    }

    @Test
    @DisplayName("표준입력에서 읽는다 - 브라우저 조각을 이어 붙인 스트림이다")
    void reads_from_stdin() {
        assertThat(flagValue(buildFor("video/mp4;codecs=avc1", SessionType.BROADCAST), "-i"))
                .isEqualTo("pipe:0");
    }

    @Test
    @DisplayName("오디오 방송은 비디오 인자가 아예 없다")
    void audio_broadcast_has_no_video_args() {
        List<String> cmd = buildFor("audio/webm;codecs=opus", SessionType.AUDIO_BROADCAST);

        assertThat(cmd).contains("-vn");
        assertThat(cmd).doesNotContain("-g", "libx264");
    }
}
