package com.edu.edumeet.member.service;

import com.edu.edumeet.member.domain.RefreshToken;
import com.edu.edumeet.member.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshToken save(Long memberId, RefreshToken refreshToken) {
        log.info("RefreshToken 저장 요청 - memberId: {}", memberId);

        if (memberId == null) {
            log.error("memberId가 null입니다.");
            throw new IllegalArgumentException("memberId는 필수입니다.");
        }

        if (refreshToken == null) {
            log.error("refreshToken이 null입니다.");
            throw new IllegalArgumentException("refreshToken은 필수입니다.");
        }

        Optional<RefreshToken> existingToken = refreshTokenRepository.findByMemberId(memberId);

        RefreshToken savedToken;
        if (existingToken.isPresent()) {
            log.info("기존 RefreshToken 발견, 업데이트 진행 - memberId: {}", memberId);
            RefreshToken existing = existingToken.get();
            existing.updateToken(refreshToken.getToken(), refreshToken.getExpiration());
            savedToken = refreshTokenRepository.save(existing); // ✅ JpaRepository의 save(Entity) 사용
        } else {
            log.info("새로운 RefreshToken 저장 진행 - memberId: {}", memberId);
            savedToken = refreshTokenRepository.save(refreshToken); // ✅ JpaRepository의 save(Entity) 사용
        }

        if (savedToken != null) {
            log.info("RefreshToken 저장 성공 확인 - memberId: {}", memberId);
            return savedToken;
        } else {
            log.error("RefreshToken 저장 실패 - memberId: {}", memberId);
            throw new RuntimeException("RefreshToken 저장에 실패했습니다.");
        }
    }

    public RefreshToken update(Long memberId, RefreshToken refreshToken) {
        log.info("RefreshToken 업데이트 요청 - memberId: {}", memberId);

        RefreshToken existingToken = refreshTokenRepository.findByMemberId(memberId)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원의 RefreshToken을 찾을 수 없습니다."));

        existingToken.updateToken(refreshToken.getToken(), refreshToken.getExpiration());
        RefreshToken updatedToken = refreshTokenRepository.save(existingToken); // ✅ JpaRepository의 save(Entity) 사용

        log.info("RefreshToken 업데이트 완료 - memberId: {}", memberId);
        return updatedToken;
    }

    public void deleteByMemberId(Long memberId) {
        log.info("RefreshToken 삭제 요청 - memberId: {}", memberId);
        refreshTokenRepository.deleteByMemberId(memberId);
    }

    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByMemberId(Long memberId) {
        return refreshTokenRepository.findByMemberId(memberId);
    }

    @Transactional(readOnly = true)
    public boolean isValid(String token, LocalDateTime now) {
        return findByToken(token)
                .map(refreshToken -> !refreshToken.isExpired(now))
                .orElse(false);
    }
}