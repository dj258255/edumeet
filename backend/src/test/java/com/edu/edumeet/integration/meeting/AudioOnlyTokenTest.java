package com.edu.edumeet.integration.meeting;

import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.domain.SessionType;
import com.edu.edumeet.meeting.service.MeetingService;
import com.edu.edumeet.member.domain.Member;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 오디오 전용 방송에서 서버가 비디오 발행을 막는가. (#72)
 *
 * <p><b>{@code isAudioOnly()} 는 선언만 되어 있고 프로덕션에서 아무도 쓰지 않았다.</b>
 * "오디오 전용" 이 클라이언트 UI 관례로만 존재했다는 뜻이다 -
 * 클라이언트를 고치거나 토큰을 그대로 다른 SDK 에 넣으면 카메라가 올라간다.
 *
 * <p><b>왜 중요한가.</b> 측정한 egress 는 시청자당 1.42 Mbps 였고 이건 비디오 기준이다.
 * 오디오 전용의 비용 모델은 여기서 출발하는데, <b>전제를 서버가 지키지 않으면
 * 그 비용 계산 자체가 근거를 잃는다.</b>
 *
 * <p>토큰(JWT)의 {@code video} 클레임을 직접 열어서 본다.
 * 서비스가 무엇을 "의도했는지" 가 아니라 <b>SFU 에게 실제로 무엇을 말했는지</b> 를 봐야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("오디오 전용 방송은 서버가 비디오 발행을 막는다")
class AudioOnlyTokenTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired MeetingService meetingService;
    @Autowired TransactionTemplate transactionTemplate;
    @PersistenceContext EntityManager em;

    private Long givenMeeting(SessionType type) {
        String email = "audio-" + SEQ.incrementAndGet() + "@test";
        Long[] id = new Long[1];
        transactionTemplate.executeWithoutResult(status -> {
            Member owner = Member.builder().email(email).nickname("호스트").password("x").build();
            em.persist(owner);
            ClassRoom classRoom = ClassRoom.builder()
                    .member(owner).title("클래스").description("-")
                    .participantLimit(30).isDeleted(false).build();
            em.persist(classRoom);
            Meeting meeting = Meeting.builder()
                    .classRoom(classRoom).title("세션 " + type).description("-")
                    .sessionType(type).startTime(LocalDateTime.now()).build();
            em.persist(meeting);
            em.flush();
            id[0] = meeting.getId();
        });
        return id[0];
    }

    /** JWT 의 payload 를 열어 video 그랜트를 꺼낸다. 서명 검증은 목적이 아니다. */
    private JsonNode videoGrant(String jwt) throws Exception {
        String payload = jwt.split("\\.")[1];
        byte[] decoded = Base64.getUrlDecoder().decode(payload);
        return MAPPER.readTree(new String(decoded, StandardCharsets.UTF_8)).path("video");
    }

    private JsonNode hostGrantFor(SessionType type) throws Exception {
        Long meetingId = givenMeeting(type);
        Map<String, Object> result =
                meetingService.joinSession(meetingId, "audio-host-" + SEQ.incrementAndGet() + "@test", true);
        return videoGrant((String) result.get("token"));
    }

    @Test
    @DisplayName("★ 오디오 방송 호스트 토큰은 마이크만 허용한다 - 카메라·화면공유는 없다")
    void audio_broadcast_host_can_publish_microphone_only() throws Exception {
        JsonNode video = hostGrantFor(SessionType.AUDIO_BROADCAST);

        assertThat(video.path("canPublish").asBoolean())
                .as("호스트는 발행할 수 있어야 한다. 막으면 방송이 안 된다")
                .isTrue();

        List<String> sources = MAPPER.convertValue(
                video.path("canPublishSources"), MAPPER.getTypeFactory()
                        .constructCollectionType(List.class, String.class));

        assertThat(sources)
                .as("오디오 전용인데 소스 제한이 없으면 클라이언트 하나로 비디오가 올라간다")
                .containsExactly("microphone");
    }

    /**
     * <b>클레임이 없는 것이 결정이다.</b>
     *
     * <p>LiveKit 은 {@code canPublishSources} 가 없으면 모든 소스를 허용한다.
     * 비디오 세션에 카메라·마이크·화면공유를 나열해도 표현되는 정책이 없고,
     * <b>오늘의 소스 목록을 얼려서 LiveKit 이 소스를 추가하면 조용히 막히게 만들 뿐이다.</b>
     * 이 테스트는 "빠뜨린 것" 과 "일부러 안 넣은 것" 을 구분해 고정한다.
     */
    @ParameterizedTest
    @EnumSource(value = SessionType.class, names = {"INTERACTIVE", "BROADCAST"})
    @DisplayName("비디오 세션은 소스를 제한하지 않는다 - 클레임이 없는 것이 결정이다")
    void video_sessions_do_not_restrict_sources(SessionType type) throws Exception {
        JsonNode video = hostGrantFor(type);

        assertThat(video.path("canPublish").asBoolean()).isTrue();
        assertThat(video.has("canPublishSources"))
                .as("%s 에 소스 목록을 박으면 LiveKit 이 소스를 추가할 때 화상 세션이 조용히 막힌다", type)
                .isFalse();
    }

    @Test
    @DisplayName("오디오 방송 청취자는 애초에 발행 자체를 못 한다")
    void audio_broadcast_listener_cannot_publish() throws Exception {
        Long meetingId = givenMeeting(SessionType.AUDIO_BROADCAST);
        Map<String, Object> result = meetingService.joinSession(meetingId, "listener@test", false);

        assertThat(videoGrant((String) result.get("token")).path("canPublish").asBoolean()).isFalse();
        assertThat(result.get("canPublish")).isEqualTo(false);
    }
}
