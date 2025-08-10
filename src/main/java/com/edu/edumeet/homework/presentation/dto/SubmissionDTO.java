package com.edu.edumeet.homework.presentation.dto;

import com.edu.edumeet.attachment.presentation.dto.AttchmentDTO;
import com.edu.edumeet.homework.domain.SubmissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 제출물 DTO
 * 제출물 정보를 전송하기 위한 데이터 객체
 */
@Schema(description = "제출물 정보")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubmissionDTO {

    @Schema(description = "제출물 ID", example = "1")
    private Long id;

    @Schema(description = "과제 ID", example = "1", required = true)
    private Long assignmentId;

    @Schema(description = "과제 제목", example = "Spring Boot 과제")
    private String assignmentTitle;

    @Schema(description = "클래스 멤버 ID", example = "1", required = true)
    private Long classMemberId;

    @Schema(description = "클래스 멤버 이름", example = "김학생", required = true)
    private String classMemberName;

    @Schema(description = "제출물 내용", example = "과제를 완료했습니다.")
    private String content;

    @Schema(description = "제출 상태", example = "SUBMITTED")
    private SubmissionStatus status;

    @Schema(description = "제출 파일 목록")
    private List<AttchmentDTO> submissionFiles;

    @Schema(description = "등록일시", example = "2025-07-23T19:32:00")
    private LocalDateTime regDate;

    @Schema(description = "수정일시", example = "2025-07-23T19:32:00")
    private LocalDateTime modDate;
}