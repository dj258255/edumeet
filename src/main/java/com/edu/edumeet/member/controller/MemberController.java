package com.edu.edumeet.member.controller;

import com.edu.edumeet.config.jwt.JwtService;
import com.edu.edumeet.email.presentation.dto.request.EmailRequest;
import com.edu.edumeet.member.dto.request.LoginRequestDto;
import com.edu.edumeet.member.dto.request.RefreshTokenRequest;
import com.edu.edumeet.member.dto.request.SignupRequestDto;
import com.edu.edumeet.member.dto.response.SignupResponseDto;
import com.edu.edumeet.member.dto.response.TokenResponseDto;
import com.edu.edumeet.member.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/members")
@Log4j2
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final JwtService jwtService;

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
    public ResponseEntity<TokenResponseDto> refreshToken(@RequestBody RefreshTokenRequest refreshTokenRequest) {
        TokenResponseDto tokenResponse = memberService.refreshAccessToken(refreshTokenRequest);
        return ResponseEntity.ok(tokenResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Authorization 헤더가 필요합니다."
            ));
        }

        try {
            String token = authHeader.substring(7);
            Long memberId = jwtService.getMemberIdFromToken(token);
            memberService.logout(memberId);

            return ResponseEntity.ok(Map.of(
                    "message", "로그아웃이 완료되었습니다."
            ));
        } catch (Exception e) {
            log.error("로그아웃 중 오류 발생", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "잘못된 토큰입니다."
            ));
        }
    }

    @GetMapping("/email-check")
    public ResponseEntity<Map<String, String>> emailCheck(@RequestBody EmailRequest emailRequest) {
        memberService.emailCheck(emailRequest);
        return ResponseEntity.ok().body(Map.of(
                "message", "사용할 수 있는 이메일입니다."
        ));
    }

    @GetMapping("/search")
    public ResponseEntity<List<SignupResponseDto>> searchMembersByEmail(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            if (page < 0) {
                return ResponseEntity.badRequest().build();
            }
            if (size <= 0 || size > 100) {
                return ResponseEntity.badRequest().build();
            }

            List<SignupResponseDto> results = memberService.searchByEmail(keyword);

            int start = page * size;
            int end = Math.min(start + size, results.size());

            if (start >= results.size()) {
                return ResponseEntity.ok(List.of());
            }

            List<SignupResponseDto> pagedResults = results.subList(start, end);

            return ResponseEntity.ok(pagedResults);

        } catch (IllegalArgumentException e) {
            log.warn("검색 요청 검증 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("회원 검색 중 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}
