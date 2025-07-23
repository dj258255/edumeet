package com.edu.edumeet.repository;


import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.reply.domain.Reply;
import com.edu.edumeet.reply.infrastructure.ReplyJpaEntity;
import com.edu.edumeet.reply.infrastructure.ReplyJpaRepository;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Log4j2
public class ReplyRepositoryTests {

    @Autowired
    private ReplyJpaRepository replyJpaRepository;

    @Test
    public void testInsert() {
        //실제 DB에 있는 board_id
        Long id = 100L;

        Board board = Board.builder().id(id).build();

        Reply reply = Reply.builder()
                .boardId(id)
                .replyText("댓글.....")
                .replayer("replyer1")
                .build();

        replyJpaRepository.save(ReplyJpaEntity.fromDomain(reply));
    }
}