package com.edu.edumeet.integration.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인증이 실제로 요구되는지 검증한다.
 *
 * <p>이전에는 `SecurityConfig` 의 permitAll 목록 마지막에 <b>{@code "/api/v1/**"}</b> 이 있어서
 * 위에 정성껏 나열한 15개 경로가 전부 무의미했고 {@code anyRequest().authenticated()} 가
 * 사실상 죽은 코드였다. <b>API 전체가 인증 없이 열려 있었다.</b> (#23)
 *
 * <p><b>이 테스트가 따로 필요한 이유</b> — 기존 컨트롤러 테스트는
 * {@code MockMvcBuilders.webAppContextSetup(...)} 을 쓰는데 이건 <b>시큐리티 필터 체인을 타지 않는다.</b>
 * 그래서 인증이 뚫려 있어도 전부 통과한다. {@code @AutoConfigureMockMvc} 는 필터를 적용한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiAuthenticationRequiredTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("보호 대상 API 는 인증 없이 접근하면 401 이다")
    void 보호대상_API_는_인증을_요구한다() throws Exception {
        mockMvc.perform(get("/api/v1/classroom"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/meetingroom/1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/meeting/summary/1"))
                .andExpect(status().isUnauthorized());

        // 서버 간 경로도 사용자 JWT 로는 못 뚫는다. X-Internal-Token 이 있어야 한다. (#27)
        mockMvc.perform(post("/api/v1/internal/meetings/1/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그인·회원가입 같은 공개 경로는 401 이 아니다")
    void 공개_경로는_인증을_요구하지_않는다() throws Exception {
        // 401 이 아니기만 하면 된다. 본문이 없어 400 이 나올 수 있는데 그건 인증 통과의 증거다.
        mockMvc.perform(post("/api/v1/members/login"))
                .andExpect(result ->
                        org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
                                .as("공개 경로가 401 이면 안 된다")
                                .isNotEqualTo(401));

        mockMvc.perform(get("/api/v1/members/check-email").param("email", "a@b.c"))
                .andExpect(result ->
                        org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
                                .as("공개 경로가 401 이면 안 된다")
                                .isNotEqualTo(401));
    }

    @Test
    @DisplayName("LiveKit webhook 은 permitAll 이지만 서명이 없으면 401 이다")
    void webhook_은_서명을_검증한다() throws Exception {
        // 시큐리티는 통과하지만 WebhookReceiver 가 서명을 검증해 거부한다.
        mockMvc.perform(post("/api/v1/livekit/webhook")
                        .header("Authorization", "invalid-token")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
