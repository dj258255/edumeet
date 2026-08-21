package com.edu.edumeet.homework.controller;

import com.edu.edumeet.homework.service.AssignmentService;
import com.edu.edumeet.homework.dto.AssignmentCreateDTO;
import com.edu.edumeet.homework.dto.AssignmentDTO;
import com.edu.edumeet.member.domain.SecurityMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Assignment", description = "과제 관리 API")
@RestController
@RequestMapping("/api/v1/class/{classId}/assignments")
@RequiredArgsConstructor
@Log4j2
public class AssignmentController {

    private final AssignmentService assignmentService;

    @Operation(summary = "과제 생성", description = "새로운 과제를 생성합니다.")
    @PostMapping
    public ResponseEntity<Long> createAssignment(
            @Parameter(description = "클래스 ID") @PathVariable Long classId,
            @Valid @RequestBody AssignmentCreateDTO assignmentCreateDTO) {
        log.info("과제 생성 요청: classId={}, title={}", classId, assignmentCreateDTO.getTitle());
        
        Long assignmentId = assignmentService.createAssignment(assignmentCreateDTO, classId);
        return ResponseEntity.status(HttpStatus.CREATED).body(assignmentId);
    }

    @Operation(summary = "과제 조회", description = "과제 ID로 과제 정보를 조회합니다. 첨부파일과 제출현황을 포함합니다.")
    @GetMapping("/{id}")
    public ResponseEntity<AssignmentDTO> getAssignment(
            @Parameter(description = "클래스 ID") @PathVariable Long classId,
            @Parameter(description = "과제 ID") @PathVariable Long id) {
        log.debug("과제 조회 요청: classId={}, ID={}", classId, id);
        
        AssignmentDTO assignment = assignmentService.getAssignmentWithAllDetails(id);
        return ResponseEntity.ok(assignment);
    }


    @Operation(summary = "과제 삭제", description = "과제를 삭제합니다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAssignment(
            @Parameter(description = "클래스 ID") @PathVariable Long classId,
            @Parameter(description = "과제 ID") @PathVariable Long id) {
        log.info("과제 삭제 요청: classId={}, ID={}", classId, id);
        
        assignmentService.deleteAssignment(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "클래스별 과제 목록 조회", description = "특정 클래스의 모든 과제를 조회합니다. 로그인한 사용자의 제출 상태를 포함합니다.")
    @GetMapping
    public ResponseEntity<List<AssignmentDTO>> getAssignmentsByClassId(
            @Parameter(description = "클래스 ID") @PathVariable Long classId) {
        log.debug("클래스별 과제 목록 조회 요청: classId={}", classId);
        
        // 현재 로그인한 사용자의 이메일 가져오기
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUserEmail = null;
        
        if (authentication != null && authentication.getPrincipal() instanceof SecurityMember) {
            SecurityMember securityMember = (SecurityMember) authentication.getPrincipal();
            currentUserEmail = securityMember.getEmail();
            log.debug("현재 사용자 이메일: {}", currentUserEmail);
        }
        
        List<AssignmentDTO> assignments;
        if (currentUserEmail != null) {
            // 현재 사용자의 제출 상태를 포함한 과제 목록 조회
            assignments = assignmentService.getAssignmentsByClassIdWithUserStatus(classId, currentUserEmail);
        } else {
            // 인증되지 않은 사용자는 기본 과제 목록 조회
            assignments = assignmentService.getAssignmentsByClassId(classId);
        }
        
        return ResponseEntity.ok(assignments);
    }



    @Operation(summary = "과제 복원", description = "삭제된 과제를 복원합니다.")
    @PatchMapping("/{id}/restore")
    public ResponseEntity<String> restoreAssignment(
            @Parameter(description = "클래스 ID") @PathVariable Long classId,
            @Parameter(description = "과제 ID") @PathVariable Long id) {
        log.info("과제 복원 요청: classId={}, ID={}", classId, id);
        
        assignmentService.restoreAssignment(id);
        return ResponseEntity.ok("과제가 성공적으로 복원되었습니다.");
    }
}
