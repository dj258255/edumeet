package com.edu.edumeet.integration.security;

import com.edu.edumeet.config.jwt.JwtService;
import com.edu.edumeet.member.domain.Member;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 토큰이 잘못됐을 때의 상태 코드를 고정한다. (#209)
 *
 * <h3>왜 필요한가</h3>
 * 운영에서 <b>위조 토큰과 만료 토큰으로 {@code GET /api/v1/members/me} 를 부르면 200 · 0바이트</b>였다.
 * 401 이어야 한다. 필터가 JWT 해석 예외까지 삼켜서, 응답을 아무도 쓰지 않은 채 요청이 "성공" 으로 끝났다.
 * 그 200 은 클라이언트에게 "로그인돼 있다" 로 보인다.
 *
 * <p>같은 이유로 <b>처리되지 않은 예외</b>도 조용히 200 · 빈 본문이 됐다. 그래서 시험 전용 컨트롤러를
 * 하나 띄워, 그런 예외가 <b>5xx 로 드러나는지</b>(200 으로 사라지지 않는지) 확인한다.
 *
 * <h3>왜 실제 서버로 부르나</h3>
 * MockMvc 는 처리되지 않은 예외를 테스트로 그대로 던진다. 컨테이너가 그것을 5xx 로 만드는지는
 * 실제 포트로 물어야 보인다. (이 저장소의 시험 대부분은 MockMvc 로 충분하지만, 여기서 확인하려는 것은
 * "클라이언트가 받는 상태 코드" 다)
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
@ActiveProfiles("test")
@DisplayName("JWT 실패 상태 코드")
class JwtFailureStatusTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    /** 위조 토큰을 만들 때 쓰는 다른 키. 서버가 쓰는 키와 다르므로 서명 검증에서 걸린다. */
    private static final String FORGED_SECRET = Base64.getEncoder()
            .encodeToString("forged-secret-key-for-test-32bytes!!".getBytes(StandardCharsets.UTF_8));

    @Autowired TestRestTemplate restTemplate;
    @Autowired JwtService jwtService;
    @Autowired TransactionTemplate transactionTemplate;
    @PersistenceContext EntityManager em;

    @Value("${jwt.secret}")
    String jwtSecret;

    private String ownerEmail;
    private Long memberId;
    private String validToken;

    @BeforeEach
    void setUp() {
        ownerEmail = "jwt-status-" + SEQ.incrementAndGet() + "@test";
        transactionTemplate.executeWithoutResult(status -> {
            Member member = Member.builder().email(ownerEmail).nickname("사용자").password("x").build();
            em.persist(member);
            em.flush();
            memberId = member.getId();
        });
        validToken = jwtService.generateAccessToken(memberId, ownerEmail);
    }

    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> em.createQuery(
                        "DELETE FROM Member m WHERE m.email = :email")
                .setParameter("email", ownerEmail)
                .executeUpdate());
    }

    @Test
    @DisplayName("★ 위조 토큰은 401 이다 - 200 · 빈 본문이 아니다")
    void forged_token_is_unauthorized() {
        // 다른 키로 서명했다. 서명 검증에서 걸린다.
        String wrongSignature = Jwts.builder()
                .setSubject(ownerEmail)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(hmacKey(FORGED_SECRET), SignatureAlgorithm.HS256)
                .compact();

        assertUnauthorized(wrongSignature, "위조 토큰(다른 키로 서명)");

        // JWT 모양이 아닌 문자열도 마찬가지다.
        assertUnauthorized("not.a.jwt", "위조 토큰(JWT 모양이 아님)");
    }

    @Test
    @DisplayName("★ 만료된 토큰은 401 이다")
    void expired_token_is_unauthorized() {
        String expired = Jwts.builder()
                .setSubject(ownerEmail)
                .setIssuedAt(new Date(System.currentTimeMillis() - 7_200_000))
                .setExpiration(new Date(System.currentTimeMillis() - 3_600_000))
                .signWith(hmacKey(jwtSecret), SignatureAlgorithm.HS256)
                .compact();

        assertUnauthorized(expired, "만료 토큰");
    }

    @Test
    @DisplayName("유효한 토큰은 200 이고 본문이 있다")
    void valid_token_is_ok() {
        ResponseEntity<String> response = get("/api/v1/members/me", validToken);

        assertThat(response.getStatusCode().value())
                .as("유효 토큰은 통과해야 한다. 본문=%s", response.getBody())
                .isEqualTo(200);
        assertThat(response.getBody()).contains(ownerEmail);
    }

    @Test
    @DisplayName("★ 처리되지 않은 예외는 5xx 다 - 200 · 빈 본문으로 사라지지 않는다")
    void unhandled_exception_is_5xx() {
        ResponseEntity<String> response = get("/api/v1/test-only/boom", validToken);

        assertThat(response.getStatusCode().is5xxServerError())
                .as("예외가 삼켜져 200 · 빈 본문이 되면 안 된다. 실제=%d · 본문=%s",
                        response.getStatusCode().value(), response.getBody())
                .isTrue();
        assertThat(response.getStatusCode().value())
                .as("200 이면 예외가 사라진 것이다")
                .isNotEqualTo(200);
    }

    private void assertUnauthorized(String token, String what) {
        ResponseEntity<String> response = get("/api/v1/members/me", token);

        assertThat(response.getStatusCode().value())
                .as("%s 은 401 이어야 한다. 본문=%s", what, response.getBody())
                .isEqualTo(401);
    }

    private ResponseEntity<String> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private Key hmacKey(String base64Secret) {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
    }

    /**
     * 처리되지 않은 예외를 던지는 시험 전용 컨트롤러.
     *
     * <p>{@code @TestConfiguration} 의 {@code @Bean} 으로 등록한다. 두 가지를 확인하고 이 모양으로 정했다.
     * <ul>
     *   <li>테스트 클래스의 <b>중첩 클래스는 컴포넌트 스캔에 잡히지 않는다</b> — 그래서 빈으로 직접 등록한다</li>
     *   <li>{@code @RequestMapping} 만 있고 {@code @Controller} 계열이 아니면 핸들러로 잡히지 않는다.
     *       빈은 만들어지는데 매핑이 없어 404 가 났다. {@code @RestController} 가 필요하다</li>
     * </ul>
     */
    @TestConfiguration
    static class BoomConfig {

        @Bean
        BoomProbe boomProbe() {
            return new BoomProbe();
        }
    }

    @RestController
    @RequestMapping("/api/v1/test-only")
    static class BoomProbe {

        @GetMapping("/boom")
        public String boom() {
            throw new IllegalStateException("시험 전용 - 처리되지 않은 예외");
        }
    }
}
