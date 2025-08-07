package com.edu.edumeet.member.repository;

import com.edu.edumeet.member.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    Optional<RefreshToken> findByMemberId(Long memberId);

    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.memberId = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);

    boolean existsByMemberId(Long memberId);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.token = :token WHERE rt.memberId = :memberId")
    void updateTokenByMemberId(@Param("memberId") Long memberId, @Param("token") String token);
}
