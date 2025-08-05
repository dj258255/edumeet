package com.edu.edumeet.classroom.controller;

import com.edu.edumeet.classroom.dto.request.ClassCreateRequestDto;
import com.edu.edumeet.classroom.dto.request.ClassStatusChangeRequestDto;
import com.edu.edumeet.classroom.dto.request.EvictionRequestDto;
import com.edu.edumeet.classroom.dto.request.InviteStudentsRequestDto;
import com.edu.edumeet.classroom.dto.response.ClassInfoResponseDto;
import com.edu.edumeet.classroom.service.ClassService;
import com.edu.edumeet.member.domain.SecurityMember;
import com.edu.edumeet.member.presentation.dto.response.SignupResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "클래스룸 생성이 완료되었습니다."
        ));
    }

    @GetMapping("")
    public ResponseEntity<List<ClassInfoResponseDto>> getMyClassList(
            @AuthenticationPrincipal SecurityMember member) {
        return ResponseEntity.ok(classService.getMyClasses(member.getMemberId()));
    }

    @GetMapping("/joined")
    public ResponseEntity<List<ClassInfoResponseDto>> getJoinedClasses(
            @AuthenticationPrincipal SecurityMember member) {
        return ResponseEntity.ok(classService.getJoinedClasses(member.getMemberId()));
    }

    @GetMapping("/{classRoomId}")
    public ResponseEntity<ClassInfoResponseDto> getClassDetail(@PathVariable Long classRoomId) {
        return ResponseEntity.ok(classService.getClassDetail(classRoomId));
    }

    @DeleteMapping("/{classRoomId}")
    public ResponseEntity<Map<String, String>> deleteClass(
            @PathVariable Long classRoomId,
            @AuthenticationPrincipal SecurityMember member) {
        classService.delete(member.getMemberId(), classRoomId);
        return ResponseEntity.ok().body(Map.of(
                "message", "삭제가 완료되었습니다."
        ));
    }

    @PostMapping("/{classId}/invite")
    public ResponseEntity<Map<String, String>> inviteStudents(
            @PathVariable Long classId,
            @RequestBody InviteStudentsRequestDto request,
            @AuthenticationPrincipal SecurityMember member) {
        classService.inviteStudents(classId, member.getMemberId(), request.getEmails());
        return ResponseEntity.ok().body(Map.of(
                "message", "초대가 완료되었습니다."
        ));
    }

    @GetMapping("/invite")
    public ResponseEntity<List<ClassInfoResponseDto>> getInviteList(@AuthenticationPrincipal SecurityMember member) {
        return ResponseEntity.ok().body(classService.getInvitedClass(member.getMemberId()));
    }

    @PatchMapping("/status")
    public ResponseEntity<Map<String, String>> changeStatus(
            @AuthenticationPrincipal SecurityMember member,
            @RequestBody ClassStatusChangeRequestDto classStatusChangeRequestDto) {
        classService.changeStatus(member.getMemberId(), classStatusChangeRequestDto);
        return ResponseEntity.ok().body(Map.of(
                "message", "상태 변경을 완료하였습니다."
        ));
    }

    @GetMapping("/{classId}/members")
    public ResponseEntity<List<SignupResponseDto>> getMembers(
            @AuthenticationPrincipal SecurityMember member,
            @PathVariable Long classId) {
        return ResponseEntity.ok().body(classService.getClassMembers(member.getMemberId(), classId));
    }

    @DeleteMapping("/{classId}/eviction")
    public ResponseEntity<Map<String, String>> evict(
            @AuthenticationPrincipal SecurityMember member,
            @RequestBody EvictionRequestDto evictionRequestDto) {
        classService.evictStudent(member.getMemberId(), evictionRequestDto);
        return ResponseEntity.ok(Map.of("message", "강제 퇴장 처리되었습니다."));
    }
}
