package com.edu.edumeet.member.application;

import com.edu.edumeet.member.application.repository.MemberRepository;
import com.edu.edumeet.member.domain.Member;
import com.edu.edumeet.member.domain.Password;
import com.edu.edumeet.member.presentation.dto.request.SignupRequestDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("[MemberService 테스트]")
@ActiveProfiles("test")
public class MemberOpenviduServiceImplTest {
    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private MemberServiceImpl memberService;

    @Test
    @DisplayName("정상적인 회원가입 테스트")
    void signup_정상_회원가입() {
        // given
        SignupRequestDto dto = new SignupRequestDto("test@email.com", "1234", "tester");

        // Mock 설정
        given(memberRepository.existsByEmail(dto.getEmail())).willReturn(false);
        given(passwordEncoder.encode(dto.getPassword())).willReturn("encoded_pw");

        // 🔥 핵심: save() 메서드가 Member 객체를 반환하도록 Mock 설정
        Member savedMember = Member.of(1L, dto.getEmail(), Password.of("encoded_pw"), dto.getNickname());
        given(memberRepository.save(any(Member.class))).willReturn(savedMember);

        // when
        memberService.signup(dto);

        // then
        verify(memberRepository).existsByEmail(dto.getEmail());
        verify(passwordEncoder).encode(dto.getPassword());
        verify(memberRepository).save(any(Member.class));
    }

    @Test
    @DisplayName("이메일 중복으로 인한 회원가입 실패 테스트")
    void signup_실패_이메일_중복() {
        // given
        SignupRequestDto dto = new SignupRequestDto("test@email.com", "1234", "tester");
        given(memberRepository.existsByEmail(dto.getEmail())).willReturn(true);

        // when & then
        assertThatThrownBy(() -> memberService.signup(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 존재하는 회원입니다.");

        verify(memberRepository, never()).save(any(Member.class));
    }
}