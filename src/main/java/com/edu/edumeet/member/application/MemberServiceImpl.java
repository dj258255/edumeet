package com.edu.edumeet.member.application;

import com.edu.edumeet.config.jwt.CustomUserDetailsService;
import com.edu.edumeet.config.jwt.JwtService;
import com.edu.edumeet.member.application.repository.MemberRepository;
import com.edu.edumeet.member.domain.Member;
import com.edu.edumeet.member.domain.Password;
import com.edu.edumeet.member.presentation.dto.request.LoginRequestDto;
import com.edu.edumeet.member.presentation.dto.request.SignupRequestDto;
import com.edu.edumeet.member.presentation.dto.response.TokenResponseDto;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Transactional
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final JwtService jwtService;

    @Override
    public void signup(SignupRequestDto signupRequestDto) {
        String email = signupRequestDto.getEmail();
        String nickname = signupRequestDto.getNickname();

        validateIsExistsMember(email);

        Password password = Password.encode(signupRequestDto.getPassword(), passwordEncoder);
        Member member = Member.create(email, password, nickname);

        memberRepository.save(member);
    }

    @Override
    public TokenResponseDto login(LoginRequestDto loginRequestDto) {
        String email = loginRequestDto.getEmail();
        String password = loginRequestDto.getPassword();

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        authenticate(email, password);
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        checkPassword(password, userDetails.getPassword());

        String accessToken = jwtService.generateToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        return TokenResponseDto.from(email, accessToken, refreshToken);
    }

    private void validateIsExistsMember(String email) {
        if (memberRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("이미 존재하는 회원입니다.");
        }
    }

    private void authenticate(String email, String password) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
        } catch (DisabledException e) {
            throw new IllegalArgumentException("존재하지 않는 회원입니다.");
        } catch (BadCredentialsException e) {
            throw new IllegalArgumentException("틀린 비밀번호입니다.");
        }
    }

    private void checkPassword(String rawPassword, String encodedPassword) {
        if (!passwordEncoder.matches(rawPassword, encodedPassword)) {
            throw new IllegalArgumentException("틀린 비밀번호입니다.");
        }
    }
}
