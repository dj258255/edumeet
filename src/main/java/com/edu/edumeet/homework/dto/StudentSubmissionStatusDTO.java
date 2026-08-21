package com.edu.edumeet.homework.dto;

import com.edu.edumeet.attachment.presentation.dto.AttachmentDTO;
import com.edu.edumeet.homework.domain.SubmissionStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 학생 제출 상태 DTO
 * StudentSubmissionStatus의 DTO 버전으로 첨부파일이 AttachmentDTO 형식으로 변환됨
 */
@Schema(description = "학생 제출 상태 정보")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StudentSubmissionStatusDTO {
    
    @Schema(description = "과제 ID", example = "1")
    private Long assignmentId;
    
    @Schema(description = "학생 이메일", example = "student@test.com")
    private String studentEmail;
    
    @Schema(description = "학생 이름", example = "김학생")
    private String studentName;
    
    @Schema(description = "제출 상태", example = "SUBMITTED")
    private SubmissionStatus status;
    
    @Schema(description = "제출 시간", example = "2025-07-23T19:32:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime submittedAt;
    
    @Schema(description = "제출된 파일들 (s3Url 포함)")
    @Builder.Default
    private List<AttachmentDTO> submissionFiles = new ArrayList<>();
}