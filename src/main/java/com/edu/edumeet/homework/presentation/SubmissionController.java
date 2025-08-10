package com.edu.edumeet.homework.presentation;

import com.edu.edumeet.homework.presentation.dto.SubmissionCreateDTO;
import com.edu.edumeet.homework.presentation.dto.SubmissionDTO;
import com.edu.edumeet.homework.presentation.dto.SubmissionUpdateDTO;
import com.edu.edumeet.s3.util.S3Uploader;
import com.edu.edumeet.upload.domain.FileUpload;
import com.edu.edumeet.upload.presentation.dto.FileUploadDTO;
import com.edu.edumeet.upload.presentation.dto.FileUploadAdapter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Submission", description = "제출물 관리 API")
@RestController
@RequestMapping("/api/v1/class/{classId}/submissions")
@RequiredArgsConstructor
@Log4j2
public class SubmissionController {

    private final SubmissionService submissionService;
    private final S3Uploader s3Uploader;
    private final FileUploadAdapter fileUploadAdapter;

    @Operation(summary = "과제 제출", description = "새로운 제출물을 제출합니다.")
    @PostMapping
    public ResponseEntity<Long> submitAssignment(
            @Parameter(description = "클래스 ID") @PathVariable Long classId,
            @Valid @RequestBody SubmissionCreateDTO submissionCreateDTO) {
        log.info("과제 제출 요청: classId={}, assignmentId={}, classMemberId={}", 
                classId, submissionCreateDTO.getAssignmentId(), submissionCreateDTO.getClassMemberId());
        
        Long submissionId = submissionService.submitAssignment(submissionCreateDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(submissionId);
    }

    @Operation(summary = "제출물 조회", description = "제출물 ID로 제출물 정보를 조회합니다.")
    @GetMapping("/{id}")
    public ResponseEntity<SubmissionDTO> getSubmission(
            @Parameter(description = "클래스 ID") @PathVariable Long classId,
            @Parameter(description = "제출물 ID") @PathVariable Long id) {
        log.debug("제출물 조회 요청: classId={}, ID={}", classId, id);
        
        SubmissionDTO submission = submissionService.getSubmission(id);
        return ResponseEntity.ok(submission);
    }

    @Operation(summary = "제출물 삭제", description = "제출물을 삭제합니다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSubmission(
            @Parameter(description = "클래스 ID") @PathVariable Long classId,
            @Parameter(description = "제출물 ID") @PathVariable Long id) {
        log.info("제출물 삭제 요청: classId={}, ID={}", classId, id);
        
        submissionService.deleteSubmission(id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "과제별 제출물 목록 조회", description = "특정 과제의 모든 제출물을 조회합니다.")
    @GetMapping("/assignment/{assignmentId}")
    public ResponseEntity<List<SubmissionDTO>> getSubmissionsByAssignmentId(
            @Parameter(description = "클래스 ID") @PathVariable Long classId,
            @Parameter(description = "과제 ID") @PathVariable Long assignmentId) {
        log.debug("과제별 제출물 목록 조회 요청: classId={}, assignmentId={}", classId, assignmentId);
        
        List<SubmissionDTO> submissions = submissionService.getSubmissionsByAssignmentId(assignmentId);
        return ResponseEntity.ok(submissions);
    }

    @Operation(summary = "학생별 제출물 목록 조회", description = "특정 학생의 모든 제출물을 조회합니다.")
    @GetMapping("/class-member/{classMemberId}")
    public ResponseEntity<List<SubmissionDTO>> getSubmissionsByClassMemberId(
            @Parameter(description = "클래스 ID") @PathVariable Long classId,
            @Parameter(description = "학생 ID") @PathVariable Long classMemberId) {
        log.debug("학생별 제출물 목록 조회 요청: classId={}, classMemberId={}", classId, classMemberId);
        
        List<SubmissionDTO> submissions = submissionService.getSubmissionsByClassMemberId(classMemberId);
        return ResponseEntity.ok(submissions);
    }

    @Operation(summary = "특정 과제의 특정 학생 제출물 조회", description = "특정 과제에 대한 특정 학생의 제출물을 조회합니다.")
    @GetMapping("/assignment/{assignmentId}/class-member/{classMemberId}")
    public ResponseEntity<SubmissionDTO> getSubmissionByAssignmentAndClassMember(
            @Parameter(description = "클래스 ID") @PathVariable Long classId,
            @Parameter(description = "과제 ID") @PathVariable Long assignmentId,
            @Parameter(description = "학생 ID") @PathVariable Long classMemberId) {
        log.debug("특정 과제의 특정 학생 제출물 조회 요청: classId={}, assignmentId={}, classMemberId={}", classId, assignmentId, classMemberId);
        
        SubmissionDTO submission = submissionService.getSubmissionByAssignmentAndClassMember(assignmentId, classMemberId);
        return ResponseEntity.ok(submission);
    }

    @Operation(summary = "제출물 파일 Presigned URL 생성", description = "제출물 파일 업로드를 위한 presigned URL을 생성합니다.")
    @PostMapping("/{id}/presigned-url")
    public ResponseEntity<Map<String, String>> generatePresignedUrl(
            @Parameter(description = "클래스 ID") @PathVariable Long classId,
            @Parameter(description = "제출물 ID") @PathVariable Long id,
            @RequestParam String fileName) {
        log.info("제출물 파일 presigned URL 생성 요청: classId={}, submissionId={}, fileName={}", classId, id, fileName);
        
        String uuid = UUID.randomUUID().toString();
        String presignedUrl = s3Uploader.generatePresignedUrl("submissions", uuid, fileName, Duration.ofMinutes(10));
        
        return ResponseEntity.ok(Map.of(
            "presignedUrl", presignedUrl,
            "uuid", uuid,
            "fileName", fileName
        ));
    }

    @Operation(summary = "제출물 파일 추가", description = "S3 업로드 완료 후 제출물에 파일 정보를 추가합니다.")
    @PostMapping("/{id}/files")
    public ResponseEntity<String> addSubmissionFile(
            @Parameter(description = "클래스 ID") @PathVariable Long classId,
            @Parameter(description = "제출물 ID") @PathVariable Long id,
            @RequestBody FileUploadDTO fileUploadDTO) {
        log.info("제출물 파일 추가 요청: classId={}, submissionId={}, fileName={}", classId, id, fileUploadDTO.getFileName());
        
        // FileUploadDTO를 FileUpload 도메인으로 변환
        FileUpload fileUpload = fileUploadAdapter.fromFileUploadDTO(fileUploadDTO);
        submissionService.addSubmissionFile(id, fileUpload);
        return ResponseEntity.ok("파일이 성공적으로 추가되었습니다.");
    }

    @Operation(summary = "첨부파일 포함 제출물 조회", description = "첨부파일을 포함한 제출물 정보를 조회합니다.")
    @GetMapping("/{id}/with-files")
    public ResponseEntity<SubmissionDTO> getSubmissionWithFiles(
            @Parameter(description = "클래스 ID") @PathVariable Long classId,
            @Parameter(description = "제출물 ID") @PathVariable Long id) {
        log.debug("첨부파일 포함 제출물 조회 요청: classId={}, ID={}", classId, id);
        
        SubmissionDTO submission = submissionService.getSubmissionWithFiles(id);
        return ResponseEntity.ok(submission);
    }

    @Operation(summary = "제출물 복원", description = "삭제된 제출물을 복원합니다.")
    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restoreSubmission(
            @Parameter(description = "클래스 ID") @PathVariable Long classId,
            @Parameter(description = "제출물 ID") @PathVariable Long id) {
        log.info("제출물 복원 요청: classId={}, ID={}", classId, id);
        
        submissionService.restoreSubmission(id);
        return ResponseEntity.ok().build();
    }
}
