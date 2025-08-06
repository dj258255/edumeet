package com.edu.edumeet.reply;

import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.infrastructure.BoardJpaEntity;
import com.edu.edumeet.board.infrastructure.BoardJpaRepository;
import com.edu.edumeet.board.presentation.dto.PageRequestDTO;
import com.edu.edumeet.board.presentation.dto.PageResponseDTO;
import com.edu.edumeet.reply.infrastructure.ReplyJpaRepository;
import com.edu.edumeet.reply.presentation.ReplyService;
import com.edu.edumeet.reply.presentation.dto.ReplyDTO;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 댓글 기능에 대한 실행 테스트와 에지 테스트를 포함하는 테스트 클래스
 * 실행 테스트: 정상적인 사용 시나리오를 테스트
 * 에지 테스트: 경계 조건이나 예외 상황을 테스트
 */
@SpringBootTest
@Log4j2
@Transactional
@ActiveProfiles("test")
public class ReplyExecutionAndEdgeTests {

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

    /**
     * 실행 테스트: 정상적인 사용 시나리오를 테스트하는 클래스
     */
    @Nested
    @DisplayName("댓글 실행 테스트")
    class ExecutionTests {
        
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
            // when
            replyService.remove(testReplyId);
            
            // then
            // 삭제된 댓글은 null이 반환되어야 함
            ReplyDTO deletedReply = replyService.read(testReplyId);
            assertThat(deletedReply).isNull();
            
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
    
    /**
     * 에지 테스트: 경계 조건이나 예외 상황을 테스트하는 클래스
     */
    @Nested
    @DisplayName("댓글 에지 테스트")
    class EdgeTests {
        
        @Test
        @DisplayName("내용이 없는 댓글 등록 에지 테스트")
        void registerReplyWithoutTextTest() {
            // given
            ReplyDTO replyDTO = ReplyDTO.builder()
                    .replyText("")  // 빈 내용
                    .replayer("edgeTester")
                    .boardId(testBoardId)
                    .build();
            
            // when & then
            assertThatThrownBy(() -> replyService.register(replyDTO))
                    .isInstanceOf(Exception.class);
            
            log.info("내용 없는 댓글 등록 실패 확인");
        }
        
        @Test
        @DisplayName("매우 긴 내용의 댓글 등록 에지 테스트")
        void registerReplyWithLongTextTest() {
            // given
            String longText = "a".repeat(1000);  // 매우 긴 내용
            
            ReplyDTO replyDTO = ReplyDTO.builder()
                    .replyText(longText)
                    .replayer("edgeTester")
                    .boardId(testBoardId)
                    .build();
            
            // when
            Long newReplyId = replyService.register(replyDTO);
            
            // then
            ReplyDTO savedReply = replyService.read(newReplyId);
            assertThat(savedReply.getReplyText()).isEqualTo(longText);
            
            log.info("긴 내용 댓글 등록 성공: 내용 길이={}", longText.length());
        }
        
        @Test
        @DisplayName("존재하지 않는 댓글 조회 에지 테스트")
        void readNonExistentReplyTest() {
            // given
            Long nonExistentId = 99999L;
            
            // when & then
            assertThatThrownBy(() -> replyService.read(nonExistentId))
                    .isInstanceOf(Exception.class);
            
            log.info("존재하지 않는 댓글 조회 실패 확인: ID={}", nonExistentId);
        }
        
        @Test
        @DisplayName("존재하지 않는 게시글에 댓글 등록 에지 테스트")
        void registerReplyToNonExistentBoardTest() {
            // given
            Long nonExistentBoardId = 99999L;
            
            ReplyDTO replyDTO = ReplyDTO.builder()
                    .replyText("존재하지 않는 게시글 테스트")
                    .replayer("edgeTester")
                    .boardId(nonExistentBoardId)
                    .build();
            
            // when & then
            assertThatThrownBy(() -> replyService.register(replyDTO))
                    .isInstanceOf(Exception.class);
            
            log.info("존재하지 않는 게시글에 댓글 등록 실패 확인: 게시글 ID={}", nonExistentBoardId);
        }
        
        @Test
        @DisplayName("존재하지 않는 부모 댓글에 대댓글 등록 에지 테스트")
        void registerChildToNonExistentParentTest() {
            // given
            Long nonExistentParentId = 99999L;
            
            ReplyDTO childReply = ReplyDTO.builder()
                    .replyText("존재하지 않는 부모 댓글 테스트")
                    .replayer("edgeTester")
                    .boardId(testBoardId)
                    .parentReplyId(nonExistentParentId)
                    .build();
            
            // when & then
            assertThatThrownBy(() -> replyService.register(childReply))
                    .isInstanceOf(Exception.class);
            
            log.info("존재하지 않는 부모 댓글에 대댓글 등록 실패 확인: 부모 댓글 ID={}", nonExistentParentId);
        }
        
        @Test
        @DisplayName("다른 게시글의 댓글에 대댓글 등록 에지 테스트")
        void registerChildToDifferentBoardParentTest() {
            // given
            // 다른 게시글 생성
            Board otherBoard = Board.builder()
                    .title("다른 게시글")
                    .content("다른 게시글 내용")
                    .writer("otherTester")
                    .classId(1L)
                    .build();
            
            BoardJpaEntity savedOtherBoard = boardJpaRepository.save(BoardJpaEntity.fromDomain(otherBoard));
            Long otherBoardId = savedOtherBoard.getId();
            
            // 다른 게시글에 댓글 생성
            ReplyDTO otherReply = ReplyDTO.builder()
                    .replyText("다른 게시글 댓글")
                    .replayer("otherReplyer")
                    .boardId(otherBoardId)
                    .build();
            
            Long otherReplyId = replyService.register(otherReply);
            
            // 다른 게시글의 댓글에 대댓글 시도
            ReplyDTO crossBoardChildReply = ReplyDTO.builder()
                    .replyText("다른 게시글 댓글에 대댓글")
                    .replayer("edgeTester")
                    .boardId(testBoardId)  // 원래 게시글 ID
                    .parentReplyId(otherReplyId)  // 다른 게시글의 댓글 ID
                    .build();
            
            // when & then
            assertThatThrownBy(() -> replyService.register(crossBoardChildReply))
                    .isInstanceOf(Exception.class);
            
            log.info("다른 게시글의 댓글에 대댓글 등록 실패 확인: 게시글 ID={}, 다른 게시글 ID={}, 다른 게시글 댓글 ID={}", 
                    testBoardId, otherBoardId, otherReplyId);
        }
        
        @Test
        @DisplayName("삭제된 댓글에 대댓글 등록 에지 테스트")
        void registerChildToDeletedParentTest() {
            // given
            // 부모 댓글 삭제
            replyService.remove(testReplyId);
            
            // 삭제된 댓글에 대댓글 시도
            ReplyDTO childToDeletedParent = ReplyDTO.builder()
                    .replyText("삭제된 댓글에 대댓글")
                    .replayer("edgeTester")
                    .boardId(testBoardId)
                    .parentReplyId(testReplyId)
                    .build();
            
            // when & then
            assertThatThrownBy(() -> replyService.register(childToDeletedParent))
                    .isInstanceOf(Exception.class);
            
            log.info("삭제된 댓글에 대댓글 등록 실패 확인: 삭제된 댓글 ID={}", testReplyId);
        }
    }
}