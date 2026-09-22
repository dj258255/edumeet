package com.edu.edumeet.integration.observability;

import com.edu.edumeet.config.internal.InternalApiTokenFilter;
import com.edu.edumeet.config.jwt.JwtService;
import com.edu.edumeet.config.logging.MeetingLogContextFilter;
import com.edu.edumeet.member.domain.Member;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 실제 보안 필터와 핸들러 매핑을 통과한 요청에만 회의 번호가 붙는지 검증한다. (#205)
 *
 * <p>사용자 API 는 {@link JwtService} 로 만든 실제 JWT 를 쓰고,
 * 내부 API 는 기존 내부 토큰 경로와 같은 공유 토큰을 쓴다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MeetingIdLogContextTest.MdcRecordingConfig.class)
@DisplayName("회의 번호 MDC")
class MeetingIdLogContextTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final long MEETING_ID = 987654L;
    private static final long CLASS_ID = 123456L;
    private static final String INTERNAL_TOKEN = "test-internal-token";

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired MdcRecordingConfig recorder;
    @PersistenceContext EntityManager em;

    private String memberEmail;
    private String userToken;

    @BeforeEach
    void setUp() {
        memberEmail = "meeting-id-log-" + SEQ.incrementAndGet() + "@test";
        transactionTemplate.executeWithoutResult(status -> {
            em.persist(Member.builder()
                    .email(memberEmail).nickname("로그 시험").password("x").build());
            em.flush();
        });
        userToken = jwtService.generateAccessToken(1L, memberEmail);
        recorder.clear();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> em.createQuery(
                        "DELETE FROM Member m WHERE m.email = :email")
                .setParameter("email", memberEmail)
                .executeUpdate());
        recorder.clear();
        MDC.clear();
    }

    @Test
    @DisplayName("★ 단수형 사용자 채팅 경로도 핸들러의 meetingId 를 기록한다")
    void user_chat_path_uses_handler_meeting_id() throws Exception {
        perform(get("/api/v1/meeting/{meetingId}/chat/recent", MEETING_ID)
                .headers(bearer(userToken)), String.valueOf(MEETING_ID));
    }

    @Test
    @DisplayName("★ 복수형 내부 자막 경로도 핸들러의 meetingId 를 기록한다")
    void internal_transcript_path_uses_handler_meeting_id() throws Exception {
        perform(get("/api/v1/internal/meetings/{meetingId}/captions/transcript", MEETING_ID)
                .header(InternalApiTokenFilter.HEADER, INTERNAL_TOKEN), String.valueOf(MEETING_ID));
    }

    @Test
    @DisplayName("★ meetingroom 의 classId 를 meetingId 로 오인하지 않는다")
    void class_id_is_not_logged_as_meeting_id() throws Exception {
        perform(get("/api/v1/meetingroom/{classId}", CLASS_ID)
                .headers(bearer(userToken)), null);
    }

    @Test
    @DisplayName("★ 단수형 meetingroom leave 경로도 핸들러의 meetingId 를 기록한다")
    void leave_path_uses_handler_meeting_id() throws Exception {
        perform(post("/api/v1/meetingroom/{meetingId}/leave", MEETING_ID)
                .headers(bearer(userToken)), String.valueOf(MEETING_ID));
    }

    private void perform(MockHttpServletRequestBuilder request, String expected) throws Exception {
        mockMvc.perform(request).andDo(result -> {
            assertThat(result.getResponse().getStatus())
                    .as("보안 필터에서 끊기면 MVC 인터셉터까지 도달하지 않는다")
                    .isNotEqualTo(401);
        });
        assertThat(recorder.values()).containsExactly(expected);
        assertThat(MDC.get(MeetingLogContextFilter.MEETING_ID)).isNull();
        recorder.clear();
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MdcRecordingConfig implements WebMvcConfigurer {

        private final List<String> values = new CopyOnWriteArrayList<>();

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new HandlerInterceptor() {
                @Override
                public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                                         jakarta.servlet.http.HttpServletResponse response,
                                         Object handler) {
                    values.add(MDC.get(MeetingLogContextFilter.MEETING_ID));
                    return true;
                }
            }).order(1);
        }

        List<String> values() {
            return values;
        }

        void clear() {
            values.clear();
        }
    }
}
