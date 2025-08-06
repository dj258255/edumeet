package com.edu.edumeet.reply.infrastructure;


import com.edu.edumeet.base.BaseEntity;
import com.edu.edumeet.board.infrastructure.BoardJpaEntity;
import com.edu.edumeet.reply.domain.Reply;
import jakarta.persistence.*;
import lombok.*;


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
public class ReplyJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private BoardJpaEntity board;

    private String replyText;

    private String replayer;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ReplyJpaEntity parentReply; // 부모 댓글 참조
    
    @Builder.Default
    private int depth = 0; // 댓글 깊이 (0: 최상위 댓글, 1: 대댓글)


    public static ReplyJpaEntity fromDomain(Reply reply) {
        ReplyJpaEntity.ReplyJpaEntityBuilder builder = ReplyJpaEntity.builder()
                .id(reply.getId())
                .replyText(reply.getReplyText())
                .replayer(reply.getReplayer())
                .depth(reply.getDepth())
                .board(BoardJpaEntity.builder()
                        .id(reply.getBoardId())
                        .build());
        
        // 부모 댓글 ID가 있는 경우 부모 댓글 참조 설정
        if (reply.getParentReplyId() != null) {
            builder.parentReply(ReplyJpaEntity.builder()
                    .id(reply.getParentReplyId())
                    .build());
        }
        
        return builder.build();
    }

    public Reply toDomain() {
        Reply.ReplyBuilder builder = Reply.builder()
                .id(this.id)
                .boardId(this.board.getId())
                .replyText(this.replyText)
                .replayer(this.replayer)
                .depth(this.depth)
                .regDate(this.getRegDate())
                .modDate(this.getModDate());
        
        // 부모 댓글이 있는 경우 부모 댓글 ID 설정
        if (this.parentReply != null) {
            builder.parentReplyId(this.parentReply.getId());
        }
        
        return builder.build();
    }
}