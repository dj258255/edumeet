package com.edu.edumeet.member.infrastructure;

import com.edu.edumeet.base.BaseEntity;
import com.edu.edumeet.member.domain.Member;

import com.edu.edumeet.member.domain.Password;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "member")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MemberJpaEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String nickname;

    public static MemberJpaEntity from(Member member) {
        return MemberJpaEntity.builder()
                .id(member.getId())
                .email(member.getEmail())
                .password(member.getPassword().getEncoded())
                .nickname(member.getNickname())
                .build();
    }

    public Member toDomain() {
        return Member.create(
                this.email,
                Password.of(this.password),
                this.nickname
        );
    }
}
