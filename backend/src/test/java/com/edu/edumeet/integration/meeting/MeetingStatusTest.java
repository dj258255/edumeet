package com.edu.edumeet.integration.meeting;

import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.config.jwt.JwtService;
import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.domain.SessionType;
import com.edu.edumeet.member.domain.Member;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 방송 시청 화면이 의존하는 세션 단건 상태. (#124)
 *
 * <p>프론트는 {@code /api/v1/meeting/{meetingId}} 에서 {@code hlsPlaylistUrl} 을 찾고 있었다.
 * 그런데 백엔드에는 그 엔드포인트가 없었다. 송출은 켜져도 시청 화면이 URL 을 못 찾는 구조다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("세션 단건 상태")
class MeetingStatusTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired TransactionTemplate tx;
    @PersistenceContext EntityManager em;

    private Long meetingId;
    private String ownerToken;
    private String outsiderToken;

    @BeforeEach
    void setUp() {
        int n = SEQ.incrementAndGet();
        String ownerEmail = "meeting-status-owner-" + n + "@test";
        String outsiderEmail = "meeting-status-outsider-" + n + "@test";

        transaction(() -> {
            Member owner = Member.builder()
                    .email(ownerEmail).nickname("주인").password("x").build();
            em.persist(owner);
            em.persist(Member.builder()
                    .email(outsiderEmail).nickname("외부인").password("x").build());

            ClassRoom classRoom = ClassRoom.builder()
                    .member(owner).title("미디어 수업").description("-")
                    .participantLimit(30).isDeleted(false).build();
            em.persist(classRoom);

            Meeting meeting = Meeting.builder()
                    .classRoom(classRoom)
                    .title("라이브 방송")
                    .description("HLS")
                    .sessionType(SessionType.BROADCAST)
                    .startTime(LocalDateTime.now())
                    .build();
            meeting.startBroadcast("self-test", "/hls/meeting-1/live.m3u8");
            em.persist(meeting);
            em.flush();
            meetingId = meeting.getId();
        });

        ownerToken = jwtService.generateAccessToken(1L, ownerEmail);
        outsiderToken = jwtService.generateAccessToken(2L, outsiderEmail);
    }

    @Test
    @DisplayName("★ 방송 상태에 HLS 주소가 담긴다 - 시청 화면이 이 값으로 붙는다")
    void get_broadcast_status() throws Exception {
        mockMvc.perform(get("/api/v1/meeting/{meetingId}", meetingId)
                        .headers(bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meetingId", is(meetingId.intValue())))
                .andExpect(jsonPath("$.sessionType", is("BROADCAST")))
                .andExpect(jsonPath("$.broadcasting", is(true)))
                .andExpect(jsonPath("$.hlsPlaylistUrl", is("/hls/meeting-1/live.m3u8")))
                .andExpect(jsonPath("$.classId", notNullValue()));
    }

    @Test
    @DisplayName("★ 남의 방송 상태는 403 - HLS 주소도 수업 자원이다")
    void outsider_cannot_get_broadcast_status() throws Exception {
        mockMvc.perform(get("/api/v1/meeting/{meetingId}", meetingId)
                        .headers(bearer(outsiderToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("인증 없이 보면 401")
    void anonymous_cannot_get_broadcast_status() throws Exception {
        mockMvc.perform(get("/api/v1/meeting/{meetingId}", meetingId))
                .andExpect(status().isUnauthorized());
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private void transaction(Runnable body) {
        tx.executeWithoutResult(status -> body.run());
    }
}
