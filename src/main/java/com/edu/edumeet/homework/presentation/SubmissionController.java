package com.edu.edumeet.homework.presentation;

import com.edu.edumeet.homework.application.AssignmentService;
import com.edu.edumeet.homework.application.SubmissionService;
import com.edu.edumeet.homework.presentation.dto.SubmissionCreateDTO;
import com.edu.edumeet.homework.presentation.dto.SubmissionDTO;
import com.edu.edumeet.homework.presentation.dto.AssignmentDTO;
import com.edu.edumeet.homework.presentation.dto.StudentSubmissionStatusDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Submission", description = "제출물 관리 API")
@RestController
@RequestMapping("/api/v1/class/{classId}/submissions")
@RequiredArgsConstructor
@Log4j2
public class SubmissionController {

    private final SubmissionService submissionService;
    private final AssignmentService assignmentService;

    @Operation(summary = "과제 제출", description = "새로운 제출물을 제출합니다.")
    @PostMapping("/assignment/{assignmentId}")
    public ResponseEntity<Long> submitAssignment(
            @Parameter(description = "클래스 ID") @PathVariable Long classId,
            @Parameter(description = "과제 ID") @PathVariable Long assignmentId,
            @Valid @RequestBody SubmissionCreateDTO submissionCreateDTO) {
        log.info("과제 제출 요청: classId={}, assignmentId={}, classMemberEmail={}", 
                classId, assignmentId, submissionCreateDTO.getClassMemberEmail());
        
        // DTO에 assignmentId 설정
        submissionCreateDTO.setAssignmentId(assignmentId);
        Long submissionId = submissionService.submitAssignment(submissionCreateDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(submissionId);
    }





    @Operation(summary = "학생용 과제 상세 조회", description = "특정 학생 관점에서 과제 상세 정보와 자신의 제출 상태를 조회합니다.")
    @GetMapping("/student/{classMemberEmail}/assignment/{assignmentId}")
    public ResponseEntity<AssignmentDTO> getAssignmentForStudent(
            @Parameter(description = "클래스 ID") @PathVariable Long classId,
            @Parameter(description = "학생 이메일") @PathVariable String classMemberEmail,
            @Parameter(description = "과제 ID") @PathVariable Long assignmentId) {
        log.debug("학생용 과제 상세 조회 요청: classId={}, classMemberEmail={}, assignmentId={}", 
                classId, classMemberEmail, assignmentId);
        
        // 과제 기본 정보 조회
        AssignmentDTO assignment = assignmentService.getAssignment(assignmentId);
        
        // 해당 학생의 제출 상태만 필터링
        List<StudentSubmissionStatusDTO> studentStatus = assignment.getStudentSubmissionStatuses()
                .stream()
                .filter(status -> status.getStudentEmail().equals(classMemberEmail))
                .toList();
        
        // 학생 관점의 AssignmentDTO 생성 (자신의 제출 상태만 포함)
        AssignmentDTO studentAssignment = AssignmentDTO.builder()
                .id(assignment.getId())
                .title(assignment.getTitle())
                .description(assignment.getDescription())
                .classId(assignment.getClassId())
                .createdByEmail(assignment.getCreatedByEmail())
                .createdByName(assignment.getCreatedByName())
                .attachmentFiles(assignment.getAttachmentFiles())
                .studentSubmissionStatuses(studentStatus)
                .regDate(assignment.getRegDate())
                .modDate(assignment.getModDate())
                .build();
        
        return ResponseEntity.ok(studentAssignment);
    }

    @Operation(summary = "학생별 과제 목록 조회", description = "특정 학생에게 할당된 과제 목록과 제출 상태를 조회합니다.")
    @GetMapping("/student/{classMemberEmail}/assignments")
    public ResponseEntity<List<AssignmentDTO>> getAssignmentsForStudent(
            @Parameter(description = "클래스 ID") @PathVariable Long classId,
            @Parameter(description = "학생 이메일") @PathVariable String classMemberEmail) {
        log.debug("학생별 과제 목록 조회 요청: classId={}, classMemberEmail={}", classId, classMemberEmail);
        
        // 클래스의 모든 과제를 조회
        List<AssignmentDTO> allAssignments = assignmentService.getAssignmentsByClassId(classId);
        
        // 각 과제에서 해당 학생의 제출 상태만 필터링
        List<AssignmentDTO> studentAssignments = allAssignments.stream()
                .map(assignment -> {
                    // 해당 학생의 제출 상태만 필터링
                    List<StudentSubmissionStatusDTO> studentStatus = assignment.getStudentSubmissionStatuses()
                            .stream()
                            .filter(status -> status.getStudentEmail().equals(classMemberEmail))
                            .toList();
                    
                    // 새로운 AssignmentDTO를 생성하여 해당 학생의 상태만 포함
                    return AssignmentDTO.builder()
                            .id(assignment.getId())
                            .title(assignment.getTitle())
                            .description(assignment.getDescription())
                            .classId(assignment.getClassId())
                            .createdByEmail(assignment.getCreatedByEmail())
                            .createdByName(assignment.getCreatedByName())
                            .attachmentFiles(assignment.getAttachmentFiles())
                            .studentSubmissionStatuses(studentStatus)
                            .regDate(assignment.getRegDate())
                            .modDate(assignment.getModDate())
                            .build();
                })
                .toList();
        
        return ResponseEntity.ok(studentAssignments);
    }
}
