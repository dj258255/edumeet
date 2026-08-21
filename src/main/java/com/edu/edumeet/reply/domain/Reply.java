package com.edu.edumeet.reply.domain;

import com.edu.edumeet.base.BaseEntity;
import com.edu.edumeet.board.domain.BoardJpaEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 댓글.
 *
 * 이전에는 domain.Reply(도메인 모델)와 infrastructure.Reply 가 분리되어 있었다.
 * 그러나 domain.Reply 는 infrastructure 패키지 안에서만 참조되었다.
 * 서비스와 컨트롤러는 ReplyDTO 로 일했고, 도메인 모델은
 * DTO -> Reply -> Reply 변환 사슬의 중간 단계로만 존재했다.
 *
 * 한 가지를 세 타입으로 다루는 비용만 있고 얻는 것이 없어 통합했다. (#15)
 */
@Entity
@Table(name = "Reply", indexes = {
        @Index(name = "idx_reply_board_id", columnList = "board_id"),
        @Index(name = "idx_reply_parent_id", columnList = "parent_id")
})
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"board", "parentReply"})
public class Reply extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private BoardJpaEntity board;

    private String replyText;

    private String replayer;

    /** 부모 댓글. null 이면 최상위 댓글이다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Reply parentReply;

    /** 댓글 깊이. 0 = 최상위, 1 = 대댓글. 최대 1계층까지만 허용한다. */
    @Builder.Default
    private int depth = 0;

    // ---- 행위 ----

    /** 대댓글인가. */
    public boolean isChildReply() {
        return this.parentReply != null;
    }

    /** 최대 깊이(1계층)에 도달했는가. */
    public boolean isMaxDepth() {
        return this.depth >= 1;
    }

    /** 연관을 거치지 않고 식별자만 필요한 곳을 위한 접근자. */
    public Long getBoardId() {
        return this.board != null ? this.board.getId() : null;
    }

    public Long getParentReplyId() {
        return this.parentReply != null ? this.parentReply.getId() : null;
    }

    public void changeReplyText(String replyText) {
        this.replyText = replyText;
    }
}
