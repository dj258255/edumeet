package com.edu.edumeet.repository;


import com.edu.edumeet.board.application.BoardSearchRepository;
import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.infrastructure.BoardJpaEntity;
import com.edu.edumeet.board.infrastructure.BoardJpaRepository;
import com.edu.edumeet.board.presentation.BoardService;
import com.edu.edumeet.reply.domain.Reply;
import com.edu.edumeet.reply.infrastructure.ReplyJpaEntity;
import com.edu.edumeet.reply.infrastructure.ReplyJpaRepository;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@SpringBootTest
@Log4j2
@Transactional
@ActiveProfiles("test")
public class ReplyRepositoryTests {

    @Autowired
    private ReplyJpaRepository replyJpaRepository;


    @Autowired
    private BoardJpaRepository boardJpaRepository;

    // 테스트용 게시글 ID를 저장할 변수
    private Long testBoardId;

    @BeforeEach
    public void 테스트_더미데이터_100개씩(){
        // 기존 데이터 삭제
        replyJpaRepository.deleteAll();
        boardJpaRepository.deleteAll();

        for(int i = 1; i <= 100; i++){
            BoardJpaEntity boardJpaEntity = BoardJpaEntity.builder()
                    .title("title..." +i)
                    .content("content..." + i)
                    .writer("user"+ (i % 10))
                    .build();

            for(int j = 0; j < 3; j++){

                if(i % 5 ==0){
                    continue;
                }
                boardJpaEntity.addImage(UUID.randomUUID().toString(), "file" + i + ".jpg");
            }
            BoardJpaEntity saved = boardJpaRepository.save(boardJpaEntity);

            if(i==1){
                testBoardId = saved.getId();
            }
        }
        log.info("테스트 데이터 생성 완료 : 첫 번째 게시글 ID :  " + testBoardId);
    }


    @Test
    public void 테스트_삽입() {
        //실제 DB에 있는 board_id
        Board board = Board.builder().id(testBoardId).build();

        Reply reply = Reply.builder()
                .boardId(testBoardId)
                .replyText("댓글.....")
                .replayer("replyer1")
                .build();

        replyJpaRepository.save(ReplyJpaEntity.fromDomain(reply));
    }
}