package com.edu.edumeet.member.infrastructure;

import com.edu.edumeet.member.domain.RefreshToken;
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
public class RefreshTokenJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 512)
    private String token;

    @Column(nullable = false)
    private LocalDateTime expiration;

    public RefreshToken toDomain() {
        return RefreshToken.create(token, expiration);
    }

    public void updateToken(String newToken, LocalDateTime newExpiration) {
        this.token = newToken;
        this.expiration = newExpiration;
    }

    @Override
    public String toString() {
        return "RefreshTokenJpaEntity{" +
                "id=" + id +
                ", memberId=" + memberId +
                ", token='" + (token != null ? token.substring(0, Math.min(token.length(), 20)) + "..." : "null") + '\'' +
                ", expiration=" + expiration +
                '}';
    }
}
