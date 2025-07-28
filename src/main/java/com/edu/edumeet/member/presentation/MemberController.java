package com.edu.edumeet.member.presentation;

import com.edu.edumeet.member.application.MemberService;
import com.edu.edumeet.member.presentation.dto.request.SignupRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/members")
@Log4j2
@RequiredArgsConstructor
public class MemberController {
    private final MemberService memberService;

    @PostMapping("/signup")
    public ResponseEntity<Map<String, String>> signup(@RequestBody SignupRequestDto signupRequestDto) {
        memberService.signup(signupRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "회원가입이 완료되었습니다."
        ));
    }
}
