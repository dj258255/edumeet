package com.edu.edumeet.integration.security;

import com.edu.edumeet.config.jwt.JwtService;
import com.edu.edumeet.member.domain.Member;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 토큰 인증 중 <b>사용자 조회가 실패</b>한 경우의 상태 코드를 고정한다. (#209)
 *
 * <h3>왜 필요한가</h3>
 * 필터가 {@code authenticate(...)} 의 예외를 넓게({@code Exception}) 잡으면,
 * {@code loadUserByUsername} 이 DB 장애로 던지는 예외까지 "토큰이 틀렸다" 로 뭉뚱그려진다.
 * 그러면 <b>장애가 인증 실패로 위장</b>되고, 클라이언트는 401 을 받아 토큰 갱신·로그아웃으로 빠진다 -
 * 서버가 아픈 것을 사용자 잘못으로 돌리는 셈이다.
 *
 * <p>토큰 오류(위조·만료·형식)와 "그 토큰의 사용자가 없다" 만 401 이고, 그 뒤의 인프라 실패는
 * <b>5xx 로 드러나야</b> 한다.
 *
 * <p>이 시험만의 컨텍스트에서 {@code UserDetailsService} 를 DB 장애를 내는 것으로 바꿔 끼운다
 * ({@code @Primary}). 다른 시험은 진짜 구현을 그대로 쓴다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
@ActiveProfiles("test")
@DisplayName("JWT 인증 중 DB 장애")
class JwtAuthenticationDbFailureTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired TestRestTemplate restTemplate;
    @Autowired JwtService jwtService;
    @Autowired TransactionTemplate transactionTemplate;
    @PersistenceContext EntityManager em;

    private String ownerEmail;
    private String token;

    @BeforeEach
    void setUp() {
        ownerEmail = "jwt-db-" + SEQ.incrementAndGet() + "@test";
        transactionTemplate.executeWithoutResult(status -> {
            Member member = Member.builder().email(ownerEmail).nickname("사용자").password("x").build();
            em.persist(member);
            em.flush();
        });
        token = jwtService.generateAccessToken(1L, ownerEmail);
    }

    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> em.createQuery(
                        "DELETE FROM Member m WHERE m.email = :email")
                .setParameter("email", ownerEmail)
                .executeUpdate());
    }

    @Test
    @DisplayName("★ 사용자 조회가 DB 장애로 실패하면 401 이 아니라 5xx 다")
    void db_failure_during_user_lookup_is_5xx_not_401() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/members/me", HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);

        assertThat(response.getStatusCode().value())
                .as("DB 장애를 401 로 위장하면 클라이언트가 토큰 갱신·로그아웃으로 빠진다. 본문=%s",
                        response.getBody())
                .isNotEqualTo(401);
        assertThat(response.getStatusCode().is5xxServerError())
                .as("인프라 실패는 5xx 로 드러나야 한다. 실제=%d · 본문=%s",
                        response.getStatusCode().value(), response.getBody())
                .isTrue();
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    /** 사용자 조회가 DB 장애로 실패하는 상황을 만든다. 이 시험의 컨텍스트에서만 적용된다. */
    @TestConfiguration
    static class DbFailureConfig {

        @Bean
        @Primary
        UserDetailsService dbFailureUserDetailsService() {
            return username -> {
                throw new DataAccessResourceFailureException("DB 장애 (시험 전용)");
            };
        }
    }
}
