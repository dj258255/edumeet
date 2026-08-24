package com.edu.edumeet.integration.member;

import com.edu.edumeet.member.service.RefreshTokenStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * refresh token 저장소. (#70)
 *
 * <p>관계형에서 Redis 로 옮기면서 <b>UNIQUE 제약이 공짜로 해주던 일을 손으로 하게 됐다.</b>
 * 그 부분이 실제로 되는지가 이 테스트의 핵심이다.
 *
 * <pre>
 *   관계형   token 컬럼 UNIQUE → 갱신하면 옛 토큰 행이 사라진다
 *   Redis    키가 두 벌       → 옛 토큰의 역방향 키를 직접 지워야 한다
 * </pre>
 *
 * <p>안 지우면 <b>옛 토큰이 TTL 까지 유효하다.</b> 로그아웃해도 안 끊기는 것과 같은 결과다.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("refresh token 저장소")
class RefreshTokenStoreTest {

    private static final AtomicLong SEQ = new AtomicLong(1000);

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired RefreshTokenStore store;

    private static final Duration TTL = Duration.ofMinutes(30);

    @Test
    @DisplayName("저장하면 양방향으로 조회된다")
    void saves_both_directions() {
        long memberId = SEQ.incrementAndGet();
        store.save(memberId, "token-a", TTL);

        assertThat(store.findTokenByMember(memberId)).contains("token-a");
        assertThat(store.findMemberByToken("token-a"))
                .as("토큰으로 회원을 찾을 수 있어야 갱신 요청을 처리할 수 있다")
                .contains(memberId);
    }

    @Test
    @DisplayName("★ 갱신하면 옛 토큰이 무효화된다 - UNIQUE 제약이 해주던 일이다")
    void replacing_invalidates_the_old_token() {
        long memberId = SEQ.incrementAndGet();
        store.save(memberId, "old-token", TTL);
        store.save(memberId, "new-token", TTL);

        assertThat(store.findMemberByToken("old-token"))
                .as("옛 토큰이 살아 있으면 로그아웃해도 안 끊기는 것과 같다")
                .isEmpty();
        assertThat(store.findMemberByToken("new-token")).contains(memberId);
        assertThat(store.findTokenByMember(memberId)).contains("new-token");
    }

    @Test
    @DisplayName("로그아웃하면 양쪽 키가 모두 사라진다")
    void logout_clears_both_keys() {
        long memberId = SEQ.incrementAndGet();
        store.save(memberId, "token-b", TTL);

        store.deleteByMember(memberId);

        assertThat(store.findTokenByMember(memberId)).isEmpty();
        assertThat(store.findMemberByToken("token-b"))
                .as("역방향 키를 안 지우면 토큰이 계속 유효하다")
                .isEmpty();
    }

    @Test
    @DisplayName("★ TTL 이 만료되면 조회되지 않는다 - 만료 비교를 코드로 하지 않는다")
    void expires_by_ttl() throws Exception {
        long memberId = SEQ.incrementAndGet();
        store.save(memberId, "short-lived", Duration.ofMillis(300));

        assertThat(store.findMemberByToken("short-lived")).contains(memberId);
        Thread.sleep(600);

        assertThat(store.findMemberByToken("short-lived"))
                .as("관계형이었다면 만료 컬럼을 두고 조회할 때마다 비교했어야 한다")
                .isEmpty();
        assertThat(store.findTokenByMember(memberId)).isEmpty();
    }

    @Test
    @DisplayName("없는 토큰과 null 은 빈 값이다")
    void unknown_and_null_are_empty() {
        assertThat(store.findMemberByToken("does-not-exist")).isEmpty();
        assertThat(store.findMemberByToken(null)).isEmpty();
        assertThat(store.findTokenByMember(SEQ.incrementAndGet())).isEmpty();
    }
}
