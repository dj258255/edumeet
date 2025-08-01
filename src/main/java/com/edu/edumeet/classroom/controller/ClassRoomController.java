package com.edu.edumeet.classroom.controller;

import com.edu.edumeet.classroom.dto.request.ClassCreateRequestDto;
import com.edu.edumeet.classroom.service.ClassService;
import com.edu.edumeet.member.domain.Member;
import com.edu.edumeet.member.domain.SecurityMember;
import com.edu.edumeet.member.infrastructure.MemberJpaEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/classroom")
@Log4j2
@RequiredArgsConstructor
public class ClassRoomController {
    private final ClassService classService;

    @PostMapping("")
    public ResponseEntity<Map<String, String>> create(
            @AuthenticationPrincipal SecurityMember member,
            @RequestBody ClassCreateRequestDto classCreateDto) {
        classService.create(member.getMemberId(), classCreateDto);
        System.out.println(member);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "클래스룸 생성이 완료되었습니다."
        ));
    }
}
