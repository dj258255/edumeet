package com.edu.edumeet.member.domain;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class RefreshToken {
    private String token;
    private LocalDateTime expiration;

    protected RefreshToken() {}

    private RefreshToken(String token, LocalDateTime expiration) {
        this.token = token;
        this.expiration = expiration;
    }

    public static RefreshToken create(String token, LocalDateTime expiration) {
        return new RefreshToken(token, expiration);
    }

    public boolean isExpired(LocalDateTime now) {
        return expiration.isBefore(now);
    }
}
