package com.edu.edumeet.board.domain;

import lombok.*;

import java.time.LocalDateTime;

/**
 * 게시판 도메인 모델
 * DDD의 애그리게이트 루트 역할을 하는 도메인 객체
 */
@Getter
@Setter
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
@ToString
public class Board {
    private Long id;            // 게시글 ID
    private String title;        // 제목
    private String content;      // 내용
    private String writer;       // 작성자

    private LocalDateTime regDate;  // 등록일시
    private LocalDateTime modDate;  // 수정일시

    /**
     * 게시글 내용 변경
     * @param title 새 제목
     * @param content 새 내용
     */
    public void change(String title, String content) {
        this.title = title;
        this.content = content;
    }
}
