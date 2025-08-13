package com.edu.edumeet.homework.presentation.dto;

import com.edu.edumeet.attachment.presentation.dto.AttachmentDTO;
import com.edu.edumeet.homework.domain.StudentSubmissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 과제 DTO
 * 과제 정보를 전송하기 위한 데이터 객체
 */
@Schema(description = "과제 정보")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AssignmentDTO {

    @Schema(description = "과제 ID", example = "1")
    private Long id;

    @Schema(description = "과제 제목", example = "스프링 과제", required = true)
    private String title;

    @Schema(description = "과제 설명", example = "스프링 부트를 이용한 게시판 만들기")
    private String description;

    @Schema(description = "클래스 ID", example = "1", required = true)
    private Long classId;

    @Schema(description = "과제 생성자 ID", example = "1", required = true)
    private String createdByEmail;

    @Schema(description = "과제 생성자 이름", example = "홍길동", required = true)
    private String createdByName;

    @Schema(description = "첨부 파일 목록")
    private List<AttachmentDTO> attachmentFiles;

    @Schema(description = "학생별 제출 현황")
    private List<StudentSubmissionStatus> studentSubmissionStatuses;

    @Schema(description = "등록일시", example = "2025-07-23T19:32:00")
    private LocalDateTime regDate;

    @Schema(description = "수정일시", example = "2025-07-23T19:32:00")
    private LocalDateTime modDate;
}