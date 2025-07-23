package com.edu.edumeet.board.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 게시글 DTO
 * 게시글 정보를 전송하기 위한 데이터 객체
 */
@Schema(description = "게시글 정보")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BoardDTO {

    @Schema(description = "게시글 ID", example = "1")
    private Long id;

    @Schema(description = "게시글 제목", example = "안녕하세요", required = true)
    @NotEmpty(message = "제목은 필수 입력값입니다")
    @Size(min = 3, max = 100, message = "제목은 3자 이상 100자 이하여야 합니다")
    private String title;

    @Schema(description = "게시글 내용", example = "게시글 내용입니다", required = true)
    @NotEmpty(message = "내용은 필수 입력값입니다")
    private String content;

    @Schema(description = "작성자", example = "홍길동", required = true)
    @NotEmpty(message = "작성자는 필수 입력값입니다")
    private String writer;

    @Schema(description = "등록일시", example = "2025-07-23T19:32:00")
    private LocalDateTime regDate;

    @Schema(description = "수정일시", example = "2025-07-23T19:32:00")
    private LocalDateTime modDate;
}
