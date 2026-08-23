package com.edu.edumeet.integration.security;

import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.config.jwt.JwtService;
import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.domain.SessionType;
import com.edu.edumeet.member.domain.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 남의 클래스 자원에 접근할 수 없어야 한다. (#62)
 *
 * <p>두 엔드포인트가 <b>인증만 요구하고 인가는 하지 않았다.</b>
 * <pre>
 *   GET /api/v1/meeting/summary/{classId}      회의 요약본 = 수업 STT 전문
 *   GET /api/v1/meetingroom/room/{roomName}    방 정보
 * </pre>
 *
 * <p>{@code MeetingController} 의 엔드포인트 5개 중 <b>4개는 {@code @AuthenticationPrincipal}
 * 이 있는데 하나만 없었다.</b> 패턴은 이미 있고 하나만 빠진 형태라 눈으로는 잘 안 걸린다.
 *
 * <p>진짜 JWT 로 검증한다. 필터가 DB 에서 사용자를 로드하므로 실제 경로가 그대로 돈다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("클래스 자원 인가")
class ClassResourceAuthorizationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired TransactionTemplate transactionTemplate;
    @PersistenceContext EntityManager em;

    private Long classId;
    private String roomName;
    private String ownerToken;
    private String outsiderToken;

    @BeforeEach
    void setUp() {
        int n = SEQ.incrementAndGet();
        String owner = "authz-owner-" + n + "@test";
        String outsider = "authz-outsider-" + n + "@test";

        transactionTemplate.executeWithoutResult(status -> {
            Member ownerMember = Member.builder()
                    .email(owner).nickname("주인").password("x").build();
            em.persist(ownerMember);
            em.persist(Member.builder().email(outsider).nickname("외부인").password("x").build());

            ClassRoom classRoom = ClassRoom.builder()
                    .member(ownerMember).title("클래스").description("-")
                    .participantLimit(30).isDeleted(false).build();
            em.persist(classRoom);

            Meeting meeting = Meeting.builder()
                    .classRoom(classRoom).title("회의").description("-")
                    .sessionType(SessionType.INTERACTIVE)
                    .startTime(LocalDateTime.now()).build();
            em.persist(meeting);
            em.flush();

            classId = classRoom.getId();
            roomName = "meeting-" + meeting.getId();
        });

        ownerToken = jwtService.generateAccessToken(1L, owner);
        outsiderToken = jwtService.generateAccessToken(2L, outsider);
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    @DisplayName("★ 남의 클래스 요약본은 403 - 요약본은 수업 STT 전문이다")
    void outsider_cannot_read_summary() throws Exception {
        mockMvc.perform(get("/api/v1/meeting/summary/{classId}", classId)
                        .headers(bearer(outsiderToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("클래스 주인은 요약본에 접근할 수 있다")
    void owner_can_read_summary() throws Exception {
        int status = mockMvc.perform(get("/api/v1/meeting/summary/{classId}", classId)
                        .headers(bearer(ownerToken)))
                .andReturn().getResponse().getStatus();

        assertThat(status)
                .as("요약본이 아직 없으므로 204 가 정상이다. 403 이면 인가가 과하게 걸린 것이다")
                .isNotEqualTo(403);
    }

    @Test
    @DisplayName("★ 남의 방 정보는 403 - LiveKit 을 부르기 전에 막아야 한다")
    void outsider_cannot_read_room_info() throws Exception {
        mockMvc.perform(get("/api/v1/meetingroom/room/{roomName}", roomName)
                        .headers(bearer(outsiderToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("주인은 인가를 통과한다 - LiveKit 이 없어 503 이지만 403 은 아니다")
    void owner_passes_authorization_on_room_info() throws Exception {
        int status = mockMvc.perform(get("/api/v1/meetingroom/room/{roomName}", roomName)
                        .headers(bearer(ownerToken)))
                .andReturn().getResponse().getStatus();

        assertThat(status)
                .as("인가는 LiveKit 호출보다 먼저다. 주인이 403 을 받으면 안 된다")
                .isNotEqualTo(403);
    }

    @Test
    @DisplayName("인증 없이 부르면 401 - 403 과 구분된다")
    void anonymous_gets_401() throws Exception {
        mockMvc.perform(get("/api/v1/meeting/summary/{classId}", classId))
                .andExpect(status().isUnauthorized());
    }
}
