package com.edu.edumeet.member.domain;

import com.edu.edumeet.base.BaseEntity;
import com.edu.edumeet.classroom.domain.ClassMember;
import com.edu.edumeet.classroom.domain.ClassRoom;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

@Entity
@Table(name = "member")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Getter
public class Member extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String nickname;

    @OneToMany(mappedBy = "member")
    private List<ClassRoom> classRooms;

    @OneToMany(mappedBy = "member")
    private List<ClassMember> classMembers;

    // 정적 팩토리 메서드
    public static Member create(String email, String rawPassword, String nickname, PasswordEncoder passwordEncoder) {
        return Member.builder()
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .nickname(nickname)
                .build();
    }

    public static Member of(Long id, String email, String encodedPassword, String nickname) {
        return Member.builder()
                .id(id)
                .email(email)
                .password(encodedPassword)
                .nickname(nickname)
                .build();
    }

    // 비밀번호 관련 메서드
    public boolean matchesPassword(String rawPassword, PasswordEncoder passwordEncoder) {
        return passwordEncoder.matches(rawPassword, this.password);
    }

    public void updatePassword(String newRawPassword, PasswordEncoder passwordEncoder) {
        this.password = passwordEncoder.encode(newRawPassword);
    }

    public String getEncodedPassword() {
        return this.password;
    }
}
