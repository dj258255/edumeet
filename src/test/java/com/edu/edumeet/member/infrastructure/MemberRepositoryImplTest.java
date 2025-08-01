package com.edu.edumeet.member.infrastructure;

import com.edu.edumeet.member.domain.Member;
import com.edu.edumeet.member.domain.Password;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
@DisplayName("[MemberRepositoryImpl 테스트]")
public class MemberRepositoryImplTest {
    @Mock
    private MemberJpaRepository memberJpaRepository;

    @InjectMocks
    private MemberRepositoryImpl memberRepository;

    @Test
    void existsByEmail_이메일이존재하면_true반환() {
        String email = "test@email.com";
        given(memberJpaRepository.existsByEmail(email)).willReturn(true);

        boolean result = memberRepository.existsByEmail(email);

        assertThat(result).isTrue();
        verify(memberJpaRepository).existsByEmail(email);
    }

    @Test
    void save_도메인객체를_JpaEntity로변환후_저장한다() {
        Member member = Member.create("test@email.com", Password.of("encoded_pw"), "tester");
        given(memberJpaRepository.save(Mockito.<MemberJpaEntity>any()))
                .willReturn(MemberJpaEntity.from(member));

        Member saved = memberRepository.save(member);

        verify(memberJpaRepository).save(Mockito.<MemberJpaEntity>any());
        assertThat(saved.getEmail()).isEqualTo(member.getEmail());
    }

}
