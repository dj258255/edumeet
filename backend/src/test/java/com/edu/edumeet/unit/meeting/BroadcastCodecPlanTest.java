package com.edu.edumeet.unit.meeting;

import com.edu.edumeet.meeting.domain.BroadcastCodecPlan;
import com.edu.edumeet.meeting.domain.SessionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 브라우저 코덱으로 리먹싱이냐 재인코딩이냐를 가른다. (#123)
 *
 * <p>여기가 "거의 공짜" 와 "코어 하나를 통째로" 를 가르는 자리다.
 * 틀리면 에러가 아니라 <b>CPU 가 조용히 100% 로 붙는다.</b>
 */
@DisplayName("방송 코덱 판단")
class BroadcastCodecPlanTest {

    @Nested
    @DisplayName("리먹싱과 재인코딩")
    class RemuxOrTranscode {

        @ParameterizedTest(name = "{0} 는 그대로 옮긴다")
        @ValueSource(strings = {
                "video/mp4;codecs=avc1.42E01E,mp4a.40.2",
                "video/mp4; codecs=\"avc1.640028\"",
                "video/x-matroska;codecs=avc3,opus",
        })
        @DisplayName("★ H264 를 주면 다시 인코딩하지 않는다")
        void h264_is_copied(String mimeType) {
            BroadcastCodecPlan plan = BroadcastCodecPlan.of(mimeType, SessionType.BROADCAST);

            assertThat(plan.videoCopied()).isTrue();
            assertThat(plan.transcodesVideo()).isFalse();
            assertThat(plan.codecArgs()).containsSequence("-c:v", "copy");
        }

        @ParameterizedTest(name = "{0} 는 다시 인코딩한다")
        @ValueSource(strings = {
                "video/webm;codecs=vp8,opus",
                "video/webm;codecs=vp9,opus",
                "video/webm",
        })
        @DisplayName("★ VP8·VP9 는 MPEG-TS 에 못 담아 재인코딩이 필요하다")
        void vpx_is_transcoded(String mimeType) {
            BroadcastCodecPlan plan = BroadcastCodecPlan.of(mimeType, SessionType.BROADCAST);

            assertThat(plan.transcodesVideo()).isTrue();
            assertThat(plan.codecArgs()).containsSequence("-c:v", "libx264");
        }

        @Test
        @DisplayName("★ 재인코딩할 때 zerolatency 를 켠다 - 없으면 인코더가 프레임을 모아 두느라 지연이 는다")
        void transcode_uses_zerolatency() {
            BroadcastCodecPlan plan = BroadcastCodecPlan.of("video/webm;codecs=vp8", SessionType.BROADCAST);

            assertThat(plan.codecArgs()).containsSequence("-tune", "zerolatency");
        }

        @Test
        @DisplayName("mimeType 이 비어도 터지지 않고 안전한 쪽(재인코딩)을 고른다")
        void unknown_mime_falls_back_to_transcoding() {
            assertThat(BroadcastCodecPlan.of(null, SessionType.BROADCAST).transcodesVideo()).isTrue();
            assertThat(BroadcastCodecPlan.of("", SessionType.BROADCAST).transcodesVideo()).isTrue();
        }
    }

    @Nested
    @DisplayName("오디오 방송")
    class AudioBroadcast {

        @Test
        @DisplayName("★ 오디오 전용은 비디오를 아예 버린다 - 라디오에 화면을 실어 보낼 이유가 없다")
        void audio_only_drops_video() {
            BroadcastCodecPlan plan = BroadcastCodecPlan.of(
                    "audio/webm;codecs=opus", SessionType.AUDIO_BROADCAST);

            assertThat(plan.audioOnly()).isTrue();
            assertThat(plan.codecArgs()).contains("-vn");
            assertThat(plan.codecArgs()).doesNotContain("libx264");
        }

        @Test
        @DisplayName("★ 오디오 전용은 절대 비디오 재인코딩으로 세지 않는다 - 상한 계산이 틀어진다")
        void audio_only_never_counts_as_video_transcode() {
            // 이게 참이 되면 admit() 이 오디오 방송을 비디오 재인코딩으로 보고
            // 동시 방송을 하나로 묶어 버린다. 오디오는 그럴 이유가 없다.
            BroadcastCodecPlan plan = BroadcastCodecPlan.of(
                    "audio/webm;codecs=opus", SessionType.AUDIO_BROADCAST);

            assertThat(plan.transcodesVideo()).isFalse();
            assertThat(plan.videoCopied()).isFalse();
        }

        @Test
        @DisplayName("★ Opus 는 AAC 로 바꾼다 - MPEG-TS 가 Opus 를 표준으로 못 담는다")
        void opus_becomes_aac() {
            BroadcastCodecPlan plan = BroadcastCodecPlan.of(
                    "audio/webm;codecs=opus", SessionType.AUDIO_BROADCAST);

            assertThat(plan.audioCopied()).isFalse();
            assertThat(plan.codecArgs()).containsSequence("-c:a", "aac");
        }

        @Test
        @DisplayName("이미 AAC 면 그대로 옮긴다")
        void aac_is_copied() {
            BroadcastCodecPlan plan = BroadcastCodecPlan.of(
                    "audio/mp4;codecs=mp4a.40.2", SessionType.AUDIO_BROADCAST);

            assertThat(plan.audioCopied()).isTrue();
            assertThat(plan.codecArgs()).containsSequence("-c:a", "copy");
        }
    }

    @Test
    @DisplayName("★ 어느 경로로 갔는지 남긴다 - 조용히 재인코딩하면 CPU 가 왜 튀는지 못 찾는다")
    void the_decision_is_reported() {
        assertThat(BroadcastCodecPlan.of("video/mp4;codecs=avc1", SessionType.BROADCAST).describe())
                .contains("리먹싱");
        assertThat(BroadcastCodecPlan.of("video/webm;codecs=vp8", SessionType.BROADCAST).describe())
                .contains("재인코딩");
    }
}
