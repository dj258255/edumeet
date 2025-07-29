package com.edu.edumeet.member.domain;

import lombok.Getter;

@Getter
public class Member {
    private Long id;
    private String email;
    private Password password;
    private String nickname;

    protected Member() {}

    private Member(String email, Password password, String nickname) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
    }

    private Member(Long id, String email, Password password, String nickname) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.nickname = nickname;
    }

    public static Member create(String email, Password password, String nickname) {
        return new Member(email, password, nickname);
    }

    public static Member of(Long id, String email, Password password, String nickname) {
        return new Member(id, email, password, nickname);
    }
}
