package com.edu.edumeet.member.application.repository;

import com.edu.edumeet.member.domain.RefreshToken;

import java.util.Optional;

public interface RefreshTokenRepository {
    void save(Long memberId, RefreshToken refreshToken);
    void update(Long memberId, RefreshToken refreshToken);
    void deleteByMemberId(Long memberId);
    Optional<RefreshToken> findByToken(String token);
    Optional<RefreshToken> findByMemberId(Long memberId);
}
