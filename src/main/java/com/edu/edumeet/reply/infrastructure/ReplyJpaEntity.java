package com.edu.edumeet.reply.infrastructure;


import com.edu.edumeet.base.BaseEntity;
import com.edu.edumeet.board.infrastructure.BoardJpaEntity;
import com.edu.edumeet.reply.domain.Reply;
import jakarta.persistence.*;
import lombok.*;


@Entity
@Table(name = "Reply", indexes = {
        @Index(name = "idx_reply_board_id", columnList = "board_id")
})
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = "board")
public class ReplyJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private BoardJpaEntity board;

    private String replyText;

    private String replayer;


    public static ReplyJpaEntity fromDomain(Reply reply) {
        return ReplyJpaEntity.builder()
                .id(reply.getId())
                .replyText(reply.getReplyText())
                .replayer(reply.getReplayer())
                .board(BoardJpaEntity.builder()
                        .id(reply.getBoardId())
                        .build())
                .build();
    }

    public Reply toDomain() {
        return Reply.builder()
                .id(this.id)
                .boardId(this.board.getId())
                .replyText(this.replyText)
                .replayer(this.replayer)
                .regDate(this.getRegDate())
                .modDate(this.getModDate())
                .build();
    }
}