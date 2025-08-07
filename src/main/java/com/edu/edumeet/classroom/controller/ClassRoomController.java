package com.edu.edumeet.classroom.controller;

import com.edu.edumeet.classroom.dto.request.ClassCreateRequestDto;
import com.edu.edumeet.classroom.dto.request.ClassStatusChangeRequestDto;
import com.edu.edumeet.classroom.dto.request.EvictionRequestDto;
import com.edu.edumeet.classroom.dto.request.InviteStudentsRequestDto;
import com.edu.edumeet.classroom.dto.response.ClassInfoResponseDto;
import com.edu.edumeet.classroom.service.ClassService;
import com.edu.edumeet.member.domain.SecurityMember;
import com.edu.edumeet.member.dto.response.SignupResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

    @Operation(summary = "클래스 생성", 
               description = "JSON 형태로 클래스를 생성합니다. 미리 업로드된 썸네일 UUID를 포함할 수 있습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "클래스 생성 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> create(
            @AuthenticationPrincipal SecurityMember member,
            @RequestBody ClassCreateRequestDto classCreateDto) {
        
        log.info("클래스 생성 요청 - 제목: {}, 썸네일 UUID: {}", 
                classCreateDto.getTitle(), classCreateDto.getThumbnailUuid());
        
        classService.create(member.getMemberId(), classCreateDto);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "클래스룸 생성이 완료되었습니다."
        ));
    }

    @Operation(summary = "내가 생성한 클래스 목록 조회", description = "로그인한 사용자가 생성한 클래스 목록을 조회합니다.")
    @GetMapping("")
    public ResponseEntity<List<ClassInfoResponseDto>> getMyClassList(
            @AuthenticationPrincipal SecurityMember member) {
        return ResponseEntity.ok(classService.getMyClasses(member.getMemberId()));
    }

    @Operation(summary = "참여한 클래스 목록 조회", description = "로그인한 사용자가 참여한 클래스 목록을 조회합니다.")
    @GetMapping("/joined")
    public ResponseEntity<List<ClassInfoResponseDto>> getJoinedClasses(
            @AuthenticationPrincipal SecurityMember member) {
        return ResponseEntity.ok(classService.getJoinedClasses(member.getMemberId()));
    }

    @Operation(summary = "클래스 상세 조회", description = "특정 클래스의 상세 정보를 조회합니다.")
    @GetMapping("/{classId}")
    public ResponseEntity<ClassInfoResponseDto> getClassDetail(@PathVariable Long classId) {
        return ResponseEntity.ok(classService.getClassDetail(classId));
    }

    @Operation(summary = "클래스 삭제", description = "생성한 클래스를 삭제합니다.")
    @DeleteMapping("/{classId}")
    public ResponseEntity<Map<String, String>> deleteClass(
            @PathVariable Long classId,
            @AuthenticationPrincipal SecurityMember member) {
        classService.delete(member.getMemberId(), classId);
        return ResponseEntity.ok().body(Map.of(
                "message", "삭제가 완료되었습니다."
        ));
    }

    @Operation(summary = "학생 초대", description = "클래스에 학생들을 초대합니다.")
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

    @Operation(summary = "초대 목록 조회", description = "받은 초대 목록을 조회합니다.")
    @GetMapping("/invite")
    public ResponseEntity<List<ClassInfoResponseDto>> getInviteList(@AuthenticationPrincipal SecurityMember member) {
        return ResponseEntity.ok().body(classService.getInvitedClass(member.getMemberId()));
    }

    @Operation(summary = "초대 상태 변경", description = "받은 초대를 수락 또는 거절합니다.")
    @PatchMapping("/status")
    public ResponseEntity<Map<String, String>> changeStatus(
            @AuthenticationPrincipal SecurityMember member,
            @RequestBody ClassStatusChangeRequestDto classStatusChangeRequestDto) {
        classService.changeStatus(member.getMemberId(), classStatusChangeRequestDto);
        return ResponseEntity.ok().body(Map.of(
                "message", "상태 변경을 완료하였습니다."
        ));
    }

    @Operation(summary = "클래스 멤버 조회", description = "클래스에 참여한 멤버들을 조회합니다.")
    @GetMapping("/{classId}/members")
    public ResponseEntity<List<SignupResponseDto>> getMembers(
            @AuthenticationPrincipal SecurityMember member,
            @PathVariable Long classId) {
        return ResponseEntity.ok().body(classService.getClassMembers(member.getMemberId(), classId));
    }

    @Operation(summary = "학생 강제 퇴장", description = "클래스에서 학생을 강제로 퇴장시킵니다.")
    @DeleteMapping("/eviction")
    public ResponseEntity<Map<String, String>> evict(
            @AuthenticationPrincipal SecurityMember member,
            @RequestBody EvictionRequestDto evictionRequestDto) {
        classService.evictStudent(member.getMemberId(), evictionRequestDto);
        return ResponseEntity.ok(Map.of("message", "강제 퇴장 처리되었습니다."));
    }
}
