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
    
    @Builder.Default
    private Long parentReplyId = null; // 부모 댓글 ID (null이면 최상위 댓글)
    
    @Builder.Default
    private int depth = 0; // 댓글 깊이 (0: 최상위 댓글, 1: 대댓글)
    
    /**
     * 대댓글인지 확인
     * @return 대댓글 여부
     */
    public boolean isChildReply() {
        return parentReplyId != null;
    }
    
    /**
     * 최대 깊이에 도달했는지 확인 (최대 1계층)
     * @return 최대 깊이 도달 여부
     */
    public boolean isMaxDepth() {
        return depth >= 1; // 최대 1계층까지만 허용
    }
}
