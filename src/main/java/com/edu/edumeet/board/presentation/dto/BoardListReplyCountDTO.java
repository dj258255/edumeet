package com.edu.edumeet.board.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 게시글 목록 및 댓글 수 DTO
 * 게시글 목록 조회 시 각 게시글의 댓글 수를 포함하는 데이터 객체
 */
@Schema(description = "게시글 목록 및 댓글 수 정보")
@Data
public class BoardListReplyCountDTO {

    @Schema(description = "게시글 ID", example = "1")
    private Long id; // 게시글 아이디
    
    @Schema(description = "게시글 제목", example = "안녕하세요")
    private String title; // 제목
    
    @Schema(description = "작성자", example = "홍길동")
    private String writer; // 글쓴이
    
    @Schema(description = "등록일시", example = "2025-07-23T19:32:00")
    private LocalDateTime regDate; // 등록시간

    @Schema(description = "댓글 수", example = "5")
    private Long replyCount; // 댓글 개수
    
    @Schema(description = "좋아요 수", example = "0")
    private long favorite; // 좋아요 수
    
    @Schema(description = "싫어요 수", example = "0")
    private long dislike; // 싫어요 수
}
