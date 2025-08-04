package com.edu.edumeet.reply.domain;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class Reply {
    private Long id; //댓글 id
    private Long boardId; //게시글 id
    private String replyText;
    private String replayer;
    private LocalDateTime regDate;
    private LocalDateTime modDate;

}
