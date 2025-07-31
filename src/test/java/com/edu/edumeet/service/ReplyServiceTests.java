package com.edu.edumeet.service;

import com.edu.edumeet.board.infrastructure.BoardJpaEntity;
import com.edu.edumeet.board.infrastructure.BoardJpaRepository;
import com.edu.edumeet.reply.presentation.ReplyService;
import com.edu.edumeet.reply.presentation.dto.ReplyDTO;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Log4j2
@Transactional
@ActiveProfiles("test")
public class ReplyServiceTests {

    @Autowired
    private ReplyService replyService;

    @Autowired
    private BoardJpaRepository boardJpaRepository;

    private Long testBoardId;

    @BeforeEach
    public void 테스트_묵업데이터_삽입() {
        //기존 데이터 정리
        boardJpaRepository.deleteAll();

        // 테스트용 게시글 생성
        BoardJpaEntity boardJpaEntity = BoardJpaEntity.builder()
                .title("Test Board")
                .content("Test Content")
                .writer("testUser")
                .build();

        BoardJpaEntity saved = boardJpaRepository.save(boardJpaEntity);
        testBoardId = saved.getId();
        log.info("테스트용 게시글 생성 완료. ID: " + testBoardId);
    }


    @Test
    public void 테스트_삽입() {

        ReplyDTO replyDTO = ReplyDTO.builder()
                .replyText("ReplyDTO Text")
                .replayer("replyer")
                .boardId(testBoardId)
                .build();

        log.info(replyService.register(replyDTO));

    }

}