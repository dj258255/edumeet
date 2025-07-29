package com.edu.edumeet.member.application;

import com.edu.edumeet.member.domain.RefreshToken;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenService {
    void save(Long memberId, RefreshToken refreshToken);
    void update(Long memberId, RefreshToken refreshToken);
    void deleteByMemberId(Long memberId);
    Optional<RefreshToken> findByToken(String token);
    Optional<RefreshToken> findByMemberId(Long memberId);
    boolean isValid(String token, LocalDateTime now);
}
