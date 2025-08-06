package com.edu.edumeet.reply.presentation;

import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.infrastructure.BoardJpaEntity;
import com.edu.edumeet.board.infrastructure.BoardJpaRepository;
import com.edu.edumeet.board.presentation.dto.PageRequestDTO;
import com.edu.edumeet.board.presentation.dto.PageResponseDTO;
import com.edu.edumeet.reply.infrastructure.ReplyJpaRepository;
import com.edu.edumeet.reply.presentation.dto.ReplyDTO;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 댓글 프레젠테이션 계층에 대한 실행 테스트
 * 정상적인 사용 시나리오를 테스트
 */
@SpringBootTest
@Log4j2
@Transactional
@ActiveProfiles("test")
public class ReplyPresentationExecutionTests {

    @Autowired
    private ReplyService replyService;

    @Autowired
    private BoardJpaRepository boardJpaRepository;
    
    @Autowired
    private ReplyJpaRepository replyJpaRepository;

    private Long testBoardId;
    private Long testReplyId;

    @BeforeEach
    void setUp() {
        // 기존 데이터 정리
        replyJpaRepository.deleteAll();
        boardJpaRepository.deleteAll();
        
        // 테스트용 게시글 생성
        Board board = Board.builder()
                .title("댓글 테스트용 게시글")
                .content("댓글 테스트용 내용")
                .writer("tester")
                .classId(1L)
                .build();
        
        BoardJpaEntity savedBoard = boardJpaRepository.save(BoardJpaEntity.fromDomain(board));
        testBoardId = savedBoard.getId();
        
        // 테스트용 댓글 생성
        ReplyDTO replyDTO = ReplyDTO.builder()
                .replyText("테스트 댓글")
                .replayer("tester")
                .boardId(testBoardId)
                .build();
        
        testReplyId = replyService.register(replyDTO);
        
        log.info("테스트 준비 완료: 게시글 ID={}, 댓글 ID={}", testBoardId, testReplyId);
    }
    
    @Test
    @DisplayName("댓글 등록 실행 테스트")
    void registerReplyTest() {
        // given
        ReplyDTO replyDTO = ReplyDTO.builder()
                .replyText("새 댓글")
                .replayer("newUser")
                .boardId(testBoardId)
                .build();
        
        // when
        Long newReplyId = replyService.register(replyDTO);
        
        // then
        ReplyDTO savedReply = replyService.read(newReplyId);
        assertThat(savedReply).isNotNull();
        assertThat(savedReply.getReplyText()).isEqualTo("새 댓글");
        assertThat(savedReply.getReplayer()).isEqualTo("newUser");
        
        log.info("댓글 등록 성공: ID={}", newReplyId);
    }
    
    @Test
    @DisplayName("댓글 수정 실행 테스트")
    void modifyReplyTest() {
        // given
        ReplyDTO replyDTO = replyService.read(testReplyId);
        replyDTO.setReplyText("수정된 댓글");
        
        // when
        replyService.modify(replyDTO);
        
        // then
        ReplyDTO modifiedReply = replyService.read(testReplyId);
        assertThat(modifiedReply.getReplyText()).isEqualTo("수정된 댓글");
        
        log.info("댓글 수정 성공: ID={}", testReplyId);
    }
    
    @Test
    @DisplayName("댓글 삭제 실행 테스트")
    void removeReplyTest() {
        // given
        ReplyDTO replyDTO = replyService.read(testReplyId);
        assertThat(replyDTO).isNotNull();
        
        // when
        replyService.remove(testReplyId);
        
        // then
        // 삭제 후 조회 시 예외가 발생하거나 null이 반환되어야 함
        try {
            ReplyDTO deletedReply = replyService.read(testReplyId);
            assertThat(deletedReply).isNull();
        } catch (Exception e) {
            // 예외가 발생해도 테스트 성공
            log.info("삭제된 댓글 조회 시 예외 발생: {}", e.getMessage());
        }
        
        log.info("댓글 삭제 성공: ID={}", testReplyId);
    }
    
    @Test
    @DisplayName("게시글별 댓글 목록 조회 실행 테스트")
    void getListOfBoardTest() {
        // given
        // 추가 댓글 생성
        for (int i = 0; i < 5; i++) {
            ReplyDTO replyDTO = ReplyDTO.builder()
                    .replyText("목록 테스트 댓글 " + i)
                    .replayer("listTester")
                    .boardId(testBoardId)
                    .build();
            replyService.register(replyDTO);
        }
        
        // when
        PageRequestDTO pageRequestDTO = PageRequestDTO.builder()
                .page(1)
                .size(10)
                .build();
        
        PageResponseDTO<ReplyDTO> responseDTO = replyService.getListOfBoard(testBoardId, pageRequestDTO);
        
        // then
        assertThat(responseDTO.getDtoList()).isNotEmpty();
        assertThat(responseDTO.getDtoList().size()).isGreaterThanOrEqualTo(6); // 기존 1개 + 추가 5개
        
        log.info("게시글별 댓글 목록 조회 성공: 게시글 ID={}, 댓글 수={}", 
                testBoardId, responseDTO.getDtoList().size());
    }
    
    @Test
    @DisplayName("대댓글 등록 실행 테스트")
    void registerChildReplyTest() {
        // given
        ReplyDTO childReply = ReplyDTO.builder()
                .replyText("대댓글")
                .replayer("childReplyer")
                .boardId(testBoardId)
                .parentReplyId(testReplyId)
                .build();
        
        // when
        Long childReplyId = replyService.register(childReply);
        
        // then
        ReplyDTO savedChildReply = replyService.read(childReplyId);
        assertThat(savedChildReply).isNotNull();
        assertThat(savedChildReply.getParentReplyId()).isEqualTo(testReplyId);
        
        log.info("대댓글 등록 성공: ID={}, 부모 댓글 ID={}", childReplyId, testReplyId);
    }
}