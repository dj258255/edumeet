package com.edu.edumeet.member.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * refresh token 저장소. (#70)
 *
 * <h3>왜 관계형이 아니라 Redis 인가</h3>
 * <b>결함을 고치는 게 아니라 설계 판단이다.</b>
 * 처음에는 <i>"만료된 행을 지우는 배치가 없어 테이블이 무한히 자란다"</i> 를 근거로 들었는데
 * <b>틀렸다</b> — {@code member_id} 에 UNIQUE 가 있어 회원당 한 행이고, 로그인 때 갱신이다.
 * 행 수는 로그인 횟수가 아니라 회원 수에 비례한다.
 *
 * <p>정직한 근거는 이것이다 — <b>refresh token 은 도메인 데이터가 아니라 세션 상태다.</b>
 *
 * <pre>
 *   TTL 이 본질          만료가 데이터의 일부다
 *   관계형 질의가 없다    findByToken · findByMemberId 두 개뿐, JOIN 이 없다
 *   영속성이 덜 중요하다  잃으면 재로그인하면 된다
 * </pre>
 *
 * <b>관계형 저장소에 있을 이유가 없다.</b>
 *
 * <h3>키를 두 개 쓰는 이유와 그 대가</h3>
 * <pre>
 *   refresh:member:{memberId} → token      갱신·로그아웃에 필요
 *   refresh:token:{token}     → memberId   토큰으로 조회할 때 필요
 * </pre>
 *
 * 양방향 조회가 필요해서 두 벌을 둔다. <b>대가가 있다</b> —
 * 관계형에서는 {@code token} 컬럼의 UNIQUE 제약이 <b>갱신 시 옛 토큰을 자동으로 없애줬는데</b>,
 * 여기서는 <b>직접 지워야 한다.</b> 안 지우면 옛 토큰이 TTL 까지 살아 있다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenStore {

    private static final String MEMBER_KEY = "refresh:member:";
    private static final String TOKEN_KEY = "refresh:token:";

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 저장한다. <b>같은 회원의 옛 토큰은 무효화된다.</b>
     *
     * <p>TTL 을 명시적으로 건다. 이게 만료 처리의 전부다 —
     * 관계형이었다면 만료 컬럼을 두고 조회할 때마다 비교했어야 한다.
     */
    public void save(Long memberId, String token, Duration ttl) {
        // 옛 토큰의 역방향 키를 먼저 지운다. 안 지우면 옛 토큰이 TTL 까지 유효하다.
        findTokenByMember(memberId).ifPresent(old -> redisTemplate.delete(TOKEN_KEY + old));

        redisTemplate.opsForValue().set(MEMBER_KEY + memberId, token, ttl);
        redisTemplate.opsForValue().set(TOKEN_KEY + token, String.valueOf(memberId), ttl);
    }

    public Optional<String> findTokenByMember(Long memberId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(MEMBER_KEY + memberId));
    }

    /** 토큰이 없거나 만료됐으면 빈 값. <b>만료 비교를 코드로 하지 않는다.</b> */
    public Optional<Long> findMemberByToken(String token) {
        if (token == null) {
            return Optional.empty();
        }
        String memberId = redisTemplate.opsForValue().get(TOKEN_KEY + token);
        return memberId == null ? Optional.empty() : Optional.of(Long.valueOf(memberId));
    }

    /** 로그아웃. 양쪽 키를 모두 지운다. */
    public void deleteByMember(Long memberId) {
        findTokenByMember(memberId).ifPresent(token -> redisTemplate.delete(TOKEN_KEY + token));
        redisTemplate.delete(MEMBER_KEY + memberId);
    }
}
