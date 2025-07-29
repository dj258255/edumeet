package com.edu.edumeet.member.infrastructure;

import com.edu.edumeet.member.application.repository.RefreshTokenRepository;
import com.edu.edumeet.member.domain.RefreshToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
@Transactional
public class RefreshTokenRepositoryImpl implements RefreshTokenRepository {
    private final RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Override
    public void save(Long memberId, RefreshToken refreshToken) {
        log.info("RefreshToken 저장 시작 - memberId: {}", memberId);

        if (memberId == null) {
            throw new IllegalArgumentException("memberId는 null일 수 없습니다.");
        }

        if (refreshToken == null || refreshToken.getToken() == null) {
            throw new IllegalArgumentException("RefreshToken은 null일 수 없습니다.");
        }

        RefreshTokenJpaEntity entity = RefreshTokenJpaEntity.builder()
                .memberId(memberId)
                .token(refreshToken.getToken())
                .expiration(refreshToken.getExpiration())
                .build();

        log.debug("저장할 RefreshTokenJpaEntity: {}", entity);

        RefreshTokenJpaEntity savedEntity = refreshTokenJpaRepository.save(entity);

        log.info("RefreshToken 저장 완료 - id: {}, memberId: {}",
                savedEntity.getId(), savedEntity.getMemberId());
    }

    @Override
    public void update(Long memberId, RefreshToken refreshToken) {
        log.info("RefreshToken 업데이트 시작 - memberId: {}", memberId);

        RefreshTokenJpaEntity entity = refreshTokenJpaRepository.findByMemberId(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원의 RefreshToken입니다: " + memberId));

        log.debug("기존 RefreshTokenJpaEntity: {}", entity);

        entity.updateToken(refreshToken.getToken(), refreshToken.getExpiration());
        RefreshTokenJpaEntity updatedEntity = refreshTokenJpaRepository.save(entity);

        log.info("RefreshToken 업데이트 완료 - id: {}, memberId: {}",
                updatedEntity.getId(), updatedEntity.getMemberId());
    }

    @Override
    public void deleteByMemberId(Long memberId) {
        log.info("RefreshToken 삭제 시작 - memberId: {}", memberId);

        refreshTokenJpaRepository.deleteByMemberId(memberId);

        log.info("RefreshToken 삭제 완료 - memberId: {}", memberId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByToken(String token) {
        log.debug("RefreshToken 조회 by token - token 길이: {}",
                token != null ? token.length() : 0);

        return refreshTokenJpaRepository.findByToken(token)
                .map(RefreshTokenJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByMemberId(Long memberId) {
        log.debug("RefreshToken 조회 by memberId: {}", memberId);

        Optional<RefreshTokenJpaEntity> entity = refreshTokenJpaRepository.findByMemberId(memberId);

        if (entity.isPresent()) {
            log.debug("RefreshToken 찾음 - entity: {}", entity.get());
        } else {
            log.debug("RefreshToken 없음 - memberId: {}", memberId);
        }

        return entity.map(RefreshTokenJpaEntity::toDomain);
    }
}
