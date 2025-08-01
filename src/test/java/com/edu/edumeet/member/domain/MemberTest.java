package com.edu.edumeet.member.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@DisplayName("[Memeber 테스트]")
@ActiveProfiles("test")
public class MemberTest {
    @Test
    void create_정상_생성한다() {
        Password password = Password.of("PASSWORD");

        Member member = Member.create("email@email.com", password, "nickname");

        assertThat(member.getEmail()).isEqualTo("email@email.com");
        assertThat(member.getPassword()).isEqualTo(password);
        assertThat(member.getNickname()).isEqualTo("nickname");
    }
}
