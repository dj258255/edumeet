package com.edu.edumeet.email.presentation;

import com.edu.edumeet.email.application.EmailServiceImpl;
import com.edu.edumeet.email.presentation.dto.request.EmailRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import java.util.Map;


@Controller
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EmailController {

    private final EmailServiceImpl emailService;

    @PostMapping("/members/send-code")
    @Operation(summary = "인증코드 전송")
    public ResponseEntity<Map<String,String>> sendCodeVerificationCode(@RequestBody EmailRequest emailDTO) throws MessagingException {

        System.out.println("인증을 위한 이메일 : " + emailDTO.getEmail());
        emailService.sendEmail(emailDTO.getEmail().trim());

        // 이메일 service로 전송.

        return ResponseEntity.ok().body(Map.of(
                "message", "인증코드를 이메일로 전송했습니다."
        ));

    }



}
