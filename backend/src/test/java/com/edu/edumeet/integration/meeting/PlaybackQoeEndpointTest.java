package com.edu.edumeet.integration.meeting;

import com.edu.edumeet.config.jwt.JwtService;
import com.edu.edumeet.meeting.broadcast.qoe.PlaybackQoeReport;
import com.edu.edumeet.member.domain.Member;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 시청 품질 엔드포인트를 실제 보안 필터와 매핑을 통과시켜 검증한다. (#197)
 *
 * <p>인증은 실제 JWT 로 통과시킨다. 시험은 {@code Origin} 을 안 보내면 브라우저가 아니다 -
 * 그래서 한 시험은 운영 프론트 출처를 붙여 403 이 아님을 확인한다. (#186)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("시청 품질 엔드포인트")
class PlaybackQoeEndpointTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final long MEETING_ID = 987654L;
    private static final String FRONT_ORIGIN = "http://localhost:5173";

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired ObjectMapper objectMapper;
    @PersistenceContext EntityManager em;

    private String memberEmail;
    private String token;

    @BeforeEach
    void setUp() {
        memberEmail = "qoe-endpoint-" + SEQ.incrementAndGet() + "@test";
        transactionTemplate.executeWithoutResult(status -> {
            em.persist(Member.builder()
                    .email(memberEmail).nickname("시청자").password("x").build());
            em.flush();
        });
        token = jwtService.generateAccessToken(1L, memberEmail);
    }

    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> em.createQuery(
                        "DELETE FROM Member m WHERE m.email = :email")
                .setParameter("email", memberEmail)
                .executeUpdate());
    }

    @Test
    @DisplayName("★ 인증이 없으면 401 이다")
    void requires_authentication() throws Exception {
        mockMvc.perform(post(url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(401));
    }

    @Test
    @DisplayName("유효한 본문은 202 다")
    void accepts_valid_report() throws Exception {
        mockMvc.perform(post(url())
                        .headers(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validReport())))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(202));
    }

    @Test
    @DisplayName("★ 재생 시간이 간격을 크게 넘는 본문은 400 이다")
    void rejects_report_over_the_bounds() throws Exception {
        PlaybackQoeReport bogus = new PlaybackQoeReport(
                "s-1", 1, 30_000, 100_000, 0, 0, null, 0, false, false);

        mockMvc.perform(post(url())
                        .headers(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(bogus)))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(400));
    }

    @Test
    @DisplayName("★ 운영 프론트 출처를 붙인 POST 가 403 이 아니다")
    void browser_origin_is_not_forbidden() throws Exception {
        mockMvc.perform(post(url())
                        .headers(bearer(token))
                        .header(HttpHeaders.ORIGIN, FRONT_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validReport())))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("교차 출처 요청이 막히면 브라우저에서 아무 보고도 안 온다 (#186)")
                        .isEqualTo(202));
    }

    @Test
    @DisplayName("★ final·native 가 JSON 이름 그대로 매핑된다")
    void final_and_native_map_from_json_names() throws Exception {
        PlaybackQoeReport parsed = objectMapper.readValue("""
                {"sessionId":"s-1","seq":3,"intervalMs":30000,"playingMs":27000,"stallMs":3000,
                 "stallCount":1,"startupMs":1200,"errors":0,"final":true,"native":true}
                """, PlaybackQoeReport.class);

        assertThat(parsed.last()).isTrue();
        assertThat(parsed.nativePlayer()).isTrue();
        assertThat(parsed.seq()).isEqualTo(3);

        // 경로도 같은 이름으로 통과한다.
        mockMvc.perform(post(url())
                        .headers(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"s-1","seq":1,"intervalMs":30000,"playingMs":30000,
                                 "stallMs":0,"stallCount":0,"startupMs":null,"errors":0,
                                 "final":true,"native":true}
                                """))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(202));
    }

    private PlaybackQoeReport validReport() {
        return new PlaybackQoeReport("s-1", 1, 30_000, 27_000, 3_000, 1, 1_200L, 0, false, false);
    }

    private String json(PlaybackQoeReport report) throws Exception {
        return objectMapper.writeValueAsString(report);
    }

    private String url() {
        return "/api/v1/meeting/" + MEETING_ID + "/broadcast/qoe";
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
