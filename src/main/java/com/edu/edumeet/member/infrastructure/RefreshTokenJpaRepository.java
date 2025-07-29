package com.edu.edumeet.member.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenJpaEntity, Long> {
    Optional<RefreshTokenJpaEntity> findByToken(String token);
    Optional<RefreshTokenJpaEntity> findByMemberId(Long memberId);

    @Modifying
    @Query("DELETE FROM RefreshTokenJpaEntity r WHERE r.memberId = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);

    boolean existsByMemberId(Long memberId);
}
