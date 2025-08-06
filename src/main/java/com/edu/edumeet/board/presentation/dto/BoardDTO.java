package com.edu.edumeet.board.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

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

    @Schema(description = "클래스 ID", example = "1", required = true)
    private Long classId;
    
    @Schema(description = "카테고리 ID", example = "1")
    private Long categoryId;
    
    @Schema(description = "게시글 타입 (NORMAL: 일반, NOTICE: 공지사항, RECOMMENDED: 추천게시글)", example = "NORMAL")
    private String boardType;
    
    @Schema(description = "조회수", example = "0")
    private long view;
    
    @Schema(description = "좋아요 수", example = "0")
    private long favorite;
    
    @Schema(description = "싫어요 수", example = "0")
    private long dislike;

    //첨부파일 이름들
    // BoardJpaEntity의 Set<BoardImage> 타입으로  변환되어야함.
    // private Set<BoardImageJpaEntity> imageSet = new HashSet<>();
    // 기존의 modelmapper는 단순한 구조의 객체를 다른 타입의 객체로 만드는 데는 편리하지만
    // 다양한 처리가 필요할 땐 오히려 더 복잡하기 때문에 DTO 객체를 엔티티 객체로 변환하는 메소드를 만들자.
    // 그건 Service 인터페이스가 처리하는 경우가 많으니 Service단으로 가서 수정하자
    private List<String> fileNames;


    @Schema(description = "등록일시", example = "2025-07-23T19:32:00")
    private LocalDateTime regDate;

    @Schema(description = "수정일시", example = "2025-07-23T19:32:00")
    private LocalDateTime modDate;
}
