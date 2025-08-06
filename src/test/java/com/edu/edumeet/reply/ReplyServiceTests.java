package com.edu.edumeet.reply;

import com.edu.edumeet.board.infrastructure.BoardJpaEntity;
import com.edu.edumeet.board.infrastructure.BoardJpaRepository;
import com.edu.edumeet.board.presentation.dto.PageRequestDTO;
import com.edu.edumeet.board.presentation.dto.PageResponseDTO;
import com.edu.edumeet.reply.presentation.ReplyService;
import com.edu.edumeet.reply.presentation.dto.ReplyDTO;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Log4j2
@Transactional
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ReplyServiceTests {

    @Autowired
    private ReplyService replyService;

    @Autowired
    private BoardJpaRepository boardJpaRepository;

    private Long testBoardId;

    @BeforeAll
    public void 테스트_데이터_생성() {
        // 기존 데이터 정리
        boardJpaRepository.deleteAll();
        log.info("테스트 데이터 생성 시작");

        // 테스트용 게시글 생성
        BoardJpaEntity boardJpaEntity = BoardJpaEntity.builder()
                .title("Test Board")
                .content("Test Content")
                .classId(1L)
                .writer("testUser")
                .build();

        BoardJpaEntity saved = boardJpaRepository.save(boardJpaEntity);
        testBoardId = saved.getId();
        log.info("테스트용 게시글 생성 완료. ID: " + testBoardId);
    }

    @Test
    public void 테스트_댓글_등록_및_조회() {
        // 댓글 등록
        ReplyDTO replyDTO = ReplyDTO.builder()
                .replyText("테스트 댓글 내용")
                .replayer("테스터")
                .boardId(testBoardId)
                .build();

        Long replyId = replyService.register(replyDTO);
        log.info("등록된 댓글 ID: " + replyId);
        
        // 댓글 목록 조회
        PageRequestDTO pageRequestDTO = PageRequestDTO.builder()
                .page(1)
                .size(10)
                .build();
                
        PageResponseDTO<ReplyDTO> responseDTO = replyService.getListOfBoard(testBoardId, pageRequestDTO);
        
        assertThat(responseDTO.getDtoList()).isNotEmpty();
        assertThat(responseDTO.getDtoList().get(0).getReplyText()).isEqualTo("테스트 댓글 내용");
        assertThat(responseDTO.getDtoList().get(0).getReplayer()).isEqualTo("테스터");
        
        log.info("댓글 목록 조회 결과: {}", responseDTO);
    }
}