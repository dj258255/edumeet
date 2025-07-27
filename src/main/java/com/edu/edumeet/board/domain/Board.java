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
     * 왜 JpaEntity에 안넣고 여기에 넣음??
     *  -> 이 메서드는 도메인에 요구되는 요구사항임 -> 그래서 도메인 로직임
     *  그리고 도메인 로직은 도메인 모델이 처리하는게 더 자연스러움
     *  만약 도메인 로직이 영속성 객체에 위치하면 JpaEntity랑 분리하는 의미가 없음
     *  영속성 라이브러리가 JPA에서 MongoDB로 바뀌듯 영속성 객체의 형태가 바뀐다고 해도
     *  도메인 로직이 영향을 받아서는 안된다.
     *  그래서 Change 같은 도메인 로직은 도메인 모델인 Board가 가지고 있는게 더 자연스러움
     *  만약 영속성 객체가 도메인 로직을 가지게 된다면 영속성 코드의 변경이 도메인 로직에 영향을 줄 수 있게 된다는 의미다.
     *  결론적으로 영속성 객체에는 데이터 영속화와 관련된 코드만 들어가야 한다.
     */

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
