package com.edu.edumeet.member.application;

import com.edu.edumeet.member.application.repository.RefreshTokenRepository;
import com.edu.edumeet.member.domain.RefreshToken;
import com.edu.edumeet.member.infrastructure.RefreshTokenJpaRepository;
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
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    public void save(Long memberId, RefreshToken refreshToken) {
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

        if (existingToken.isPresent()) {
            log.info("기존 RefreshToken 발견, 업데이트 진행 - memberId: {}", memberId);
            update(memberId, refreshToken);
        } else {
            log.info("새로운 RefreshToken 저장 진행 - memberId: {}", memberId);
            refreshTokenRepository.save(memberId, refreshToken);
        }

        Optional<RefreshToken> savedToken = refreshTokenRepository.findByMemberId(memberId);
        if (savedToken.isPresent()) {
            log.info("RefreshToken 저장 성공 확인 - memberId: {}", memberId);
        } else {
            log.error("RefreshToken 저장 실패 - memberId: {}", memberId);
            throw new RuntimeException("RefreshToken 저장에 실패했습니다.");
        }
    }

    @Override
    public void update(Long memberId, RefreshToken refreshToken) {
        log.info("RefreshToken 업데이트 요청 - memberId: {}", memberId);
        refreshTokenRepository.update(memberId, refreshToken);
    }

    @Override
    public void deleteByMemberId(Long memberId) {
        log.info("RefreshToken 삭제 요청 - memberId: {}", memberId);
        refreshTokenRepository.deleteByMemberId(memberId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByMemberId(Long memberId) {
        return refreshTokenRepository.findByMemberId(memberId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isValid(String token, LocalDateTime now) {
        return findByToken(token)
                .map(refreshToken -> !refreshToken.isExpired(now))
                .orElse(false);
    }
}
