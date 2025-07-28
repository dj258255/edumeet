package com.edu.edumeet.member.application;

import com.edu.edumeet.member.application.repository.MemberRepository;
import com.edu.edumeet.member.domain.Member;
import com.edu.edumeet.member.domain.Password;
import com.edu.edumeet.member.presentation.dto.request.SignupRequestDto;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Transactional
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void signup(SignupRequestDto signupRequestDto) {
        String email = signupRequestDto.getEmail();
        String nickname = signupRequestDto.getNickname();

        validateIsExistsMember(email);

        Password password = Password.encode(signupRequestDto.getPassword(), passwordEncoder);
        Member member = Member.create(email, password, nickname);

        memberRepository.save(member);
    }

    private void validateIsExistsMember(String email) {
        if (memberRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("이미 존재하는 회원입니다.");
        }
    }
}
