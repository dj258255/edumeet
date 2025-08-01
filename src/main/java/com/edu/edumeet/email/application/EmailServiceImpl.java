package com.edu.edumeet.email.application;


import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Random;



@Service
@RequiredArgsConstructor
public class EmailServiceImpl {

    private final JavaMailSender javaMailSender;
    private final AuthCodeService authCodeService;

    @Value("${spring.mail.username}")
    private String senderEmail;


    // 랜덤으로 숫자 생성
    public String createNumber() {
        Random random = new Random();
        StringBuilder key = new StringBuilder();

        for (int i = 0; i < 8; i++) { // 인증 코드 8자리
            int index = random.nextInt(3); // 0~2까지 랜덤, 랜덤값으로 switch문 실행

            switch (index) {
                case 0 -> key.append((char) (random.nextInt(26) + 97)); // 소문자
                case 1 -> key.append((char) (random.nextInt(26) + 65)); // 대문자
                case 2 -> key.append(random.nextInt(10)); // 숫자
            }
        }
        return key.toString();
    }

    public MimeMessage createMail(String mail, String number) throws MessagingException {
        System.out.println("createMail 부분");

        MimeMessage message = javaMailSender.createMimeMessage();

        // 두 번째 파라미터 true는 multipart 메시지를 만들겠다는 의미 (첨부파일, HTML 등 지원)
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(senderEmail);
        helper.setTo(mail); // RecipientType.TO 말고도 helper에 맞게!
        helper.setSubject("이메일 인증");

        String body = "";
        body += "<h3>요청하신 인증 번호입니다.</h3>";
        body += "<h1>" + number + "</h1>";
        body += "<h3>감사합니다.</h3>";

        // 여기! helper를 사용해서 HTML 설정
        helper.setText(body, true); // true → HTML 적용

        return message;
    }


    @Async("mailExecutor")
    // 메일 발송
    public void sendEmail(String email) throws MessagingException {
        String number = createNumber(); // 랜덤 인증번호 생성
        System.out.println("랜덤 인증 번호 : " + number);
        MimeMessage message = createMail(email, number); // 메일 생성

//        // MimeMessage 전체 내용 확인용
//        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
//            message.writeTo(baos); // MimeMessage 내용을 스트림에 씀
//            String rawMessage = baos.toString("UTF-8");
//            System.out.println("📧 MimeMessage 내용:\n" + rawMessage);
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        }

        javaMailSender.send(message); // 메일 발송
        authCodeService.saveAuthCode(email, number); // 인증 코드 Redis 저장.


    }

}
