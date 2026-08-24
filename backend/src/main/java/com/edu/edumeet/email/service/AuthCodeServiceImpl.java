package com.edu.edumeet.email.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

/**
 * 이메일 인증 코드. (#62)
 *
 * <p>기본으로 꺼져 있다(#55). 그래도 고치는 이유는 <b>플래그 하나로 되살아나기 때문</b>이다.
 *
 * <h3>고친 것</h3>
 * <pre>
 *   성공해도 코드가 남아 있었다   → 만료 전까지 재사용 가능했다
 *   시도 횟수 제한이 없었다       → 6자리면 100만 번에 뚫린다
 *   equals 로 비교했다           → 상수 시간이 아니다
 *   코드를 로그에 찍었다          → 로그 접근자가 곧 계정 접근자가 된다
 * </pre>
 */
@ConditionalOnProperty(name = "edumeet.email.enabled", havingValue = "true")
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthCodeServiceImpl implements AuthCodeService {

    private static final Duration EXPIRE = Duration.ofMinutes(3);

    /**
     * 코드 하나당 허용하는 시도 횟수.
     *
     * <p>6자리 숫자면 경우의 수가 100만이다. 제한이 없으면 3분 안에 다 넣어볼 수 있다.
     * 사람이 오타를 내는 횟수로는 5회면 넉넉하다.
     */
    private static final int MAX_ATTEMPTS = 5;

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void saveAuthCode(String email, String code) {
        redisTemplate.opsForValue().set(codeKey(email), code, EXPIRE);
        // 새 코드를 받으면 시도 횟수도 초기화한다.
        redisTemplate.delete(attemptKey(email));
    }

    @Override
    public boolean verifyCode(String email, String code) {
        if (email == null || code == null) {
            return false;
        }
        String codeKey = codeKey(email);

        if (exceededAttempts(email)) {
            // 코드를 무효화한다. 남겨두면 제한을 우회해 재시도할 여지가 생긴다.
            redisTemplate.delete(codeKey);
            log.warn("인증 코드 시도 횟수 초과 - 코드를 무효화한다. email={}", email);
            return false;
        }

        String saved = redisTemplate.opsForValue().get(codeKey);
        if (saved == null) {
            return false;
        }
        // 상수 시간 비교. 바이트별로 일찍 끊으면 응답 시간으로 한 자리씩 맞출 수 있다.
        boolean matched = MessageDigest.isEqual(
                saved.getBytes(StandardCharsets.UTF_8),
                code.getBytes(StandardCharsets.UTF_8));

        if (matched) {
            // 성공하면 소비한다. 남겨두면 만료 전까지 재사용된다.
            redisTemplate.delete(codeKey);
            redisTemplate.delete(attemptKey(email));
        }
        // 코드 값 자체는 절대 로그에 남기지 않는다.
        log.debug("인증 코드 확인 - email={}, 결과={}", email, matched);
        return matched;
    }

    /** 시도를 하나 세고, 한도를 넘었는지 돌려준다. */
    private boolean exceededAttempts(String email) {
        String key = attemptKey(email);
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1L) {
            // 첫 시도에만 TTL 을 건다. 매번 걸면 공격자가 창을 무한히 연장할 수 있다.
            redisTemplate.expire(key, EXPIRE);
        }
        return attempts != null && attempts > MAX_ATTEMPTS;
    }

    private String codeKey(String email) {
        return "authcode:" + email;
    }

    private String attemptKey(String email) {
        return "authcode:attempts:" + email;
    }
}
