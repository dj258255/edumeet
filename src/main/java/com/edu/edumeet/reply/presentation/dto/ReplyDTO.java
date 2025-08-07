package com.edu.edumeet.reply.presentation.dto;


import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplyDTO {

    private Long id;

    @NotNull
    private Long boardId;

    @NotEmpty(message = "비어있으면 안된다.")
    @Size(min=1, max=255,message = "댓글내용은 1~255이하")
    private String replyText;

    @NotEmpty
    private String replayer;

    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    private LocalDateTime regDate;

    @JsonIgnore
    private LocalDateTime modDate;
    
    private Long parentReplyId; // 부모 댓글 ID (null이면 최상위 댓글)
    
    @Builder.Default
    private int depth = 0; // 댓글 깊이 (0: 최상위 댓글, 1: 대댓글)
    
    // 대댓글 목록 (조회 시 사용)
    @Builder.Default
    private List<ReplyDTO> children = new ArrayList<>();
    
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
    
    /**
     * 대댓글이 있는지 확인
     * @return 대댓글 존재 여부
     */
    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }
}
