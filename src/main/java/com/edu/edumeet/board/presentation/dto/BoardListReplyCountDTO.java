package com.edu.edumeet.board.presentation.dto;


import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BoardListReplyCountDTO {

    private Long id; //게시글 아이디
    private String title; //제목
    private String writer; //글쓴이
    private LocalDateTime regDate; //등록시간

    private Long replyCount; //댓글 개수.

}
