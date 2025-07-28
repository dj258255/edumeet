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
        this.id = null;
        this.email = email;
        this.password = password;
        this.nickname = nickname;
    }

    public static Member create(String email, Password password, String nickname) {
        return new Member(email, password, nickname);
    }
}
