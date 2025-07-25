package com.edu.edumeet.members.presentation;


import com.edu.edumeet.members.application.AuthCodeService;
import com.edu.edumeet.members.application.EmailService;
import com.edu.edumeet.members.application.MemberService;
import com.edu.edumeet.members.domain.EmailRequest;
import com.edu.edumeet.members.domain.EmailResponse;
import com.edu.edumeet.members.domain.Member;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class EmailController {

    private final MemberService memberService;
    private final EmailService emailService;
    private final AuthCodeService authCodeService;


    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody Member member){
        System.out.println("받은 이메일 : " + member.getEmail());
        System.out.println("받은 비밀번호 : " + member.getPassword());
        System.out.println("받은 이름 : " + member.getName());

        // 회원 가입
        memberService.register(member.getEmail(), member.getPassword(), member.getName());

        return ResponseEntity.ok("회원가입 완료");

    }

    //example@example.com
    @PostMapping("/members/send-code")
    @Operation(summary = "인증코드 전송")
    public ResponseEntity<Map<String,String>> sendCodeVerificationCode(@RequestBody EmailRequest emailDTO) throws MessagingException {

        System.out.println("인증을 위한 이메일 : " + emailDTO.getEmail());
        String email = emailService.sendEmail(emailDTO.getEmail().trim());

        // 이메일 service로 전송.

        return ResponseEntity.ok().body(Map.of(
                "message", "인증코드를 이메일로 전송했습니다."
        ));

    }


    @PostMapping("/members/verification")
    public ResponseEntity<?> verifyCode(@RequestBody EmailResponse  request) throws MessagingException {
        boolean result = authCodeService.verifyCode(request.getEmail(), request.getCode());
        if(result){
            return ResponseEntity.ok("인증 성공");
        }
        else{
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("인증 실패");
        }

    }

}
