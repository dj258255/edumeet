package com.edu.edumeet.homework.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 제출물 수정 DTO
 */
@Schema(description = "제출물 수정 정보")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubmissionUpdateDTO {

    @Schema(description = "제출물 내용", example = "수정된 제출물 내용입니다.")
    private String content;

    @Schema(description = "수정자 ID", example = "1", required = true)
    @NotNull(message = "수정자 ID는 필수입니다")
    private Long updaterId;
}