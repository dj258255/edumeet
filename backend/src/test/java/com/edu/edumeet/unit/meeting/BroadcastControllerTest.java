package com.edu.edumeet.unit.meeting;

import com.edu.edumeet.meeting.broadcast.BroadcastService;
import com.edu.edumeet.meeting.controller.BroadcastController;
import com.edu.edumeet.member.domain.SecurityMember;
import com.edu.edumeet.exception.CustomRestAdvice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.*;

@DisplayName("방송 시작 옵션")
class BroadcastControllerTest {

    private static void assertBadRequest(ThrowingCallable action) {
        IllegalArgumentException error = catchThrowableOfType(action, IllegalArgumentException.class);
        assertThat(error).isNotNull();
        assertThat(new CustomRestAdvice().handleIllegalArgument(error).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("옵션을 생략하면 기존 mpegts·기본 길이를 서비스에 넘긴다")
    void defaults_keep_existing_behavior() {
        BroadcastService service = mock(BroadcastService.class);
        SecurityMember member = mock(SecurityMember.class);
        when(member.getEmail()).thenReturn("host@example.com");
        when(service.start("host@example.com", 1L, "video/mp4", "mpegts", null))
                .thenReturn("/hls/meeting-1/live.m3u8");
        BroadcastController controller = new BroadcastController(service);

        controller.start(member, 1L, Map.of("mimeType", "video/mp4"));

        verify(service).start("host@example.com", 1L, "video/mp4", "mpegts", null);
    }

    @Test
    @DisplayName("허용 목록 밖의 조각 형식은 400으로 매핑될 IllegalArgumentException이다")
    void rejects_unknown_segment_type() {
        BroadcastController controller = new BroadcastController(mock(BroadcastService.class));

        assertBadRequest(() -> controller.start(
                mock(SecurityMember.class), 1L,
                Map.of("mimeType", "video/mp4", "segmentType", "not-an-ffmpeg-value")));
    }

    @Test
    @DisplayName("허용 목록 밖의 조각 길이는 400으로 매핑될 IllegalArgumentException이다")
    void rejects_unknown_hls_time() {
        BroadcastController controller = new BroadcastController(mock(BroadcastService.class));

        assertBadRequest(() -> controller.start(
                mock(SecurityMember.class), 1L,
                Map.of("mimeType", "video/mp4", "hlsTimeSec", "3")));
    }
}
