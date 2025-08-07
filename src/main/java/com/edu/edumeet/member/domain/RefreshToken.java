package com.edu.edumeet.member.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_token",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "member_id"),
                @UniqueConstraint(columnNames = "token")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Getter
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 512)
    private String token;

    @Column(nullable = false)
    private LocalDateTime expiration;

    // 정적 팩토리 메서드
    public static RefreshToken create(Long memberId, String token, LocalDateTime expiration) {
        return RefreshToken.builder()
                .memberId(memberId)
                .token(token)
                .expiration(expiration)
                .build();
    }

    // 비즈니스 로직 메서드
    public boolean isExpired(LocalDateTime now) {
        return expiration.isBefore(now);
    }

    public void updateToken(String newToken, LocalDateTime newExpiration) {
        this.token = newToken;
        this.expiration = newExpiration;
    }

    @Override
    public String toString() {
        return "RefreshTokenEntity{" +
                "id=" + id +
                ", memberId=" + memberId +
                ", token='" + (token != null ? token.substring(0, Math.min(token.length(), 20)) + "..." : "null") + '\'' +
                ", expiration=" + expiration +
                '}';
    }
}
