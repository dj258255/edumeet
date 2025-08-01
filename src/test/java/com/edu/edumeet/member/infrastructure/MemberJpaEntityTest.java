package com.edu.edumeet.member.infrastructure;

import com.edu.edumeet.member.domain.Member;
import com.edu.edumeet.member.domain.Password;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@ActiveProfiles("test")
@DisplayName("[MemberJpaEntity 테스트]")
public class MemberJpaEntityTest {

    @Test
    void from_도메인객체를_JPA엔티티로_변환한다() {
        Password password = Password.of("encoded_pw");
        Member member = Member.create("test@email.com", password, "tester");

        MemberJpaEntity entity = MemberJpaEntity.from(member);

        assertThat(entity).extracting("email", "password", "nickname")
                .containsExactly("test@email.com", "encoded_pw", "tester");
    }

    @Test
    void toDomain_JPA엔티티를_도메인객체로_변환한다() {
        MemberJpaEntity entity = MemberJpaEntity.builder()
                .email("test@email.com")
                .password("encoded_pw")
                .nickname("tester")
                .build();

        Member member = entity.toDomain();

        assertThat(member.getEmail()).isEqualTo("test@email.com");
        assertThat(member.getPassword().getEncoded()).isEqualTo("encoded_pw");
        assertThat(member.getNickname()).isEqualTo("tester");
    }

}
