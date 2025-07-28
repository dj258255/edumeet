package com.edu.edumeet.member.presentation;

import com.edu.edumeet.config.jwt.JwtService;
import com.edu.edumeet.member.application.MemberService;
import com.edu.edumeet.member.presentation.dto.request.LoginRequestDto;
import com.edu.edumeet.member.presentation.dto.request.RefreshTokenRequest;
import com.edu.edumeet.member.presentation.dto.request.SignupRequestDto;
import com.edu.edumeet.member.presentation.dto.response.TokenResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/members")
@Log4j2
@RequiredArgsConstructor
public class MemberController {
    private final MemberService memberService;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @PostMapping("/signup")
    public ResponseEntity<Map<String, String>> signup(@RequestBody SignupRequestDto signupRequestDto) {
        memberService.signup(signupRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "회원가입이 완료되었습니다."
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponseDto> login(@RequestBody LoginRequestDto loginRequestDto) {
        return ResponseEntity.ok(memberService.login(loginRequestDto));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody RefreshTokenRequest refreshTokenRequest) {
        String refreshToken = refreshTokenRequest.getRefreshToken();
        if (!jwtService.isTokenValid(refreshToken)) {
            throw new IllegalArgumentException("Refresh Token is invalid");
        }

        String username = jwtService.extractUsername(refreshToken);
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        String newAccessToken = jwtService.generateToken(userDetails);
        return ResponseEntity.ok(newAccessToken);
    }

}
