package com.edu.edumeet.email.application;


import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@ConditionalOnProperty(name = "edumeet.email.enabled", havingValue = "true")
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthCodeServiceImpl implements AuthCodeService {

    private final RedisTemplate<String, String> redisTemplate;
    private static final long EXPIRE_MINUTES = 3;

    @Override
    public void saveAuthCode(String email, String code){
        String key = "authcode:" + email;
        redisTemplate.opsForValue().set(key, code, Duration.ofMinutes(EXPIRE_MINUTES));

    }

    @Override
    public boolean verifyCode(String email, String code){

        String key = "authcode:"+email;
        String savedCode = redisTemplate.opsForValue().get(key);
        log.debug("Redis Input code : {} " , code);


        return code.equals(savedCode);
    }
}