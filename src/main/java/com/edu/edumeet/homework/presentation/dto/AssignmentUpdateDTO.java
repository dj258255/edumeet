
package com.edu.edumeet.homework.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 과제 수정 DTO
 */
@Schema(description = "과제 수정 정보")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AssignmentUpdateDTO {

    @Schema(description = "과제 제목", example = "스프링 과제", required = true)
    @NotEmpty(message = "과제 제목은 필수입니다")
    @Size(min = 1, max = 200, message = "과제 제목은 1자 이상 200자 이하여야 합니다")
    private String title;

    @Schema(description = "과제 설명", example = "스프링 부트를 이용한 게시판 만들기")
    private String description;

    @Schema(description = "수정자 ID", example = "1", required = true)
    @NotNull(message = "수정자 ID는 필수입니다")
    private Long updaterId;
}