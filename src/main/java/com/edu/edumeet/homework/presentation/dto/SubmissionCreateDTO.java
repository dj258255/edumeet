package com.edu.edumeet.homework.presentation.dto;

import com.edu.edumeet.attachment.domain.Attachment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 제출물 생성 DTO
 */
@Schema(description = "제출물 생성 정보")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubmissionCreateDTO {

    // 과제 ID는 URL path에서 설정됨 (validation 불필요)
    private Long assignmentId;

    @Schema(description = "클래스 멤버 ID", example = "1", required = true)
    @NotNull(message = "클래스 멤버 ID는 필수입니다")
    private Long classMemberId;

    @Schema(description = "클래스 멤버 이름", example = "김학생", required = true)
    @NotEmpty(message = "클래스 멤버 이름은 필수입니다")
    private String classMemberName;

    @Schema(description = "제출물 내용", example = "과제를 완료했습니다.")
    private String content;

    @Schema(description = "첨부파일 목록", example = "[]", required = true)
    @NotEmpty(message = "첨부파일은 필수입니다")
    private List<Attachment> attachmentFiles;
}