package com.edu.edumeet.reply.presentation;

import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.infrastructure.BoardJpaEntity;
import com.edu.edumeet.board.infrastructure.BoardJpaRepository;
import com.edu.edumeet.reply.infrastructure.ReplyJpaRepository;
import com.edu.edumeet.reply.presentation.dto.ReplyDTO;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 댓글 기능에 대한 엣지 케이스 테스트
 * 경계 조건이나 예외 상황을 테스트합니다.
 */
@SpringBootTest
@Log4j2
@Transactional
@ActiveProfiles("test")
public class ReplyEdgeTests {

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
    @DisplayName("매우 긴 내용의 댓글 등록 제한 테스트")
    void registerReplyWithLongTextTest() {
        // given - 테스트 데이터 준비
        String longText = "a".repeat(1000);  // 255자를 초과하는 매우 긴 내용
        
        ReplyDTO replyDTO = ReplyDTO.builder()
                .replyText(longText)
                .replayer("edgeTester")
                .boardId(testBoardId)
                .build();
        
        // when & then - Spring이 번역한 예외 타입으로 검증 
        assertThatThrownBy(() -> replyService.register(replyDTO))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)  // ← 변경
                .hasMessageContaining("댓글 내용은 255자를 초과할 수 없습니다");
        
        log.info("긴 내용 댓글 등록 제한 확인: 시도된 길이={}", longText.length());
    }
    
    @Test
    @DisplayName("내용이 없는 댓글 등록 테스트")
    void registerReplyWithoutTextTest() {
        // given - 테스트 데이터 준비
        ReplyDTO replyDTO = ReplyDTO.builder()
                .replyText("")  // 빈 내용
                .replayer("edgeTester")
                .boardId(testBoardId)
                .build();
        
        // when & then - Spring이 번역한 예외 타입으로 검증 ✅
        assertThatThrownBy(() -> replyService.register(replyDTO))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)  // ← 변경
                .hasMessageContaining("댓글 내용은 비어있을 수 없습니다");
        
        log.info("내용 없는 댓글 등록 제한 확인");
    }
    
    @Test
    @DisplayName("존재하지 않는 댓글 조회 테스트")
    void readNonExistentReplyTest() {
        // given - 테스트 데이터 준비
        Long nonExistentId = 99999L;
        
        // when & then - 테스트 실행 및 결과 검증
        // 존재하지 않는 댓글 조회 시 예외가 발생하거나 null이 반환되어야 함
        try {
            ReplyDTO nonExistentReply = replyService.read(nonExistentId);
            assertThat(nonExistentReply).isNull();
        } catch (Exception e) {
            // 예외가 발생해도 테스트 성공
            log.info("존재하지 않는 댓글 조회 시 예외 발생: {}", e.getMessage());
        }
        
        log.info("존재하지 않는 댓글 조회 실패 확인: ID={}", nonExistentId);
    }
    
    @Test
    @DisplayName("존재하지 않는 게시글에 댓글 등록 테스트")
    void registerReplyToNonExistentBoardTest() {
        // given - 테스트 데이터 준비
        Long nonExistentBoardId = 99999L;
        
        ReplyDTO replyDTO = ReplyDTO.builder()
                .replyText("존재하지 않는 게시글 테스트")
                .replayer("edgeTester")
                .boardId(nonExistentBoardId)
                .build();
        
        // when & then - 예외가 발생해야 테스트 성공
        assertThatThrownBy(() -> replyService.register(replyDTO))
                .isInstanceOf(Exception.class);
        
        log.info("존재하지 않는 게시글에 댓글 등록 제한 확인: 게시글 ID={}", nonExistentBoardId);
    }
    
    @Test
    @DisplayName("존재하지 않는 부모 댓글에 대댓글 등록 테스트")
    void registerChildToNonExistentParentTest() {
        // given - 테스트 데이터 준비
        Long nonExistentParentId = 99999L;
        
        ReplyDTO childReply = ReplyDTO.builder()
                .replyText("존재하지 않는 부모 댓글 테스트")
                .replayer("edgeTester")
                .boardId(testBoardId)
                .parentReplyId(nonExistentParentId)
                .build();
        
        // when & then - 테스트 실행 및 결과 검증
        // 존재하지 않는 부모 댓글에 대댓글 등록 시 예외가 발생해야 함
        assertThatThrownBy(() -> replyService.register(childReply))
                .isInstanceOf(Exception.class);
        
        log.info("존재하지 않는 부모 댓글에 대댓글 등록 실패 확인: 부모 댓글 ID={}", nonExistentParentId);
    }
    
    @Test
    @DisplayName("다른 게시글의 댓글에 대댓글 등록 테스트")
    void registerChildToDifferentBoardParentTest() {
        // given - 테스트 데이터 준비
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
        
        // when & then - 테스트 실행 및 결과 검증
        // 다른 게시글의 댓글에 대댓글 등록 시 예외가 발생해야 함
        assertThatThrownBy(() -> replyService.register(crossBoardChildReply))
                .isInstanceOf(Exception.class);
        
        log.info("다른 게시글의 댓글에 대댓글 등록 실패 확인: 게시글 ID={}, 다른 게시글 ID={}, 다른 게시글 댓글 ID={}", 
                testBoardId, otherBoardId, otherReplyId);
    }
    
    @Test
    @DisplayName("삭제된 댓글에 대댓글 등록 테스트")
    void registerChildToDeletedParentTest() {
        // given - 테스트 데이터 준비
        // 부모 댓글 삭제
        replyService.remove(testReplyId);
        
        // 삭제된 댓글에 대댓글 시도
        ReplyDTO childToDeletedParent = ReplyDTO.builder()
                .replyText("삭제된 댓글에 대댓글")
                .replayer("edgeTester")
                .boardId(testBoardId)
                .parentReplyId(testReplyId)
                .build();
        
        // when & then - 테스트 실행 및 결과 검증
        // 삭제된 댓글에 대댓글 등록 시 예외가 발생해야 함
        assertThatThrownBy(() -> replyService.register(childToDeletedParent))
                .isInstanceOf(Exception.class);
        
        log.info("삭제된 댓글에 대댓글 등록 실패 확인: 삭제된 댓글 ID={}", testReplyId);
    }
    
    @Test
    @DisplayName("3계층 댓글 등록 제한 테스트")
    void registerThirdLevelReplyTest() {
        // given - 테스트 데이터 준비
        // 1계층: 최상위 댓글
        ReplyDTO rootReply = ReplyDTO.builder()
                .boardId(testBoardId)
                .replyText("최상위 댓글")
                .replayer("tester")
                .build();
        Long rootReplyId = replyService.register(rootReply);
        
        // 2계층: 대댓글
        ReplyDTO childReply = ReplyDTO.builder()
                .boardId(testBoardId)
                .replyText("대댓글")
                .replayer("tester")
                .parentReplyId(rootReplyId)
                .build();
        Long childReplyId = replyService.register(childReply);
        
        // 3계층: 손자 댓글 (대댓글의 대댓글)
        ReplyDTO grandchildReply = ReplyDTO.builder()
                .boardId(testBoardId)
                .replyText("손자 댓글")
                .replayer("tester")
                .parentReplyId(childReplyId)
                .build();
        
        // when & then - 테스트 실행 및 결과 검증
        // 3계층 댓글 등록 시 예외가 발생해야 함
        assertThatThrownBy(() -> replyService.register(grandchildReply))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("대댓글에는 댓글을 달 수 없습니다");
        
        log.info("3계층 댓글 등록 제한 확인: 최상위 댓글 ID={}, 대댓글 ID={}", rootReplyId, childReplyId);
    }
    
    @Test
    @DisplayName("댓글 수정 시 부모 댓글 ID 변경 시도 테스트")
    void modifyReplyParentIdTest() {
        // given - 테스트 데이터 준비
        // 두 개의 최상위 댓글 생성
        ReplyDTO rootReply1 = ReplyDTO.builder()
                .boardId(testBoardId)
                .replyText("첫 번째 최상위 댓글")
                .replayer("tester")
                .build();
        Long rootReply1Id = replyService.register(rootReply1);
        
        ReplyDTO rootReply2 = ReplyDTO.builder()
                .boardId(testBoardId)
                .replyText("두 번째 최상위 댓글")
                .replayer("tester")
                .build();
        Long rootReply2Id = replyService.register(rootReply2);
        
        // 첫 번째 최상위 댓글에 대댓글 생성
        ReplyDTO childReply = ReplyDTO.builder()
                .boardId(testBoardId)
                .replyText("대댓글")
                .replayer("tester")
                .parentReplyId(rootReply1Id)
                .build();
        Long childReplyId = replyService.register(childReply);
        
        // 대댓글의 부모를 두 번째 최상위 댓글로 변경 시도
        ReplyDTO modifiedReply = ReplyDTO.builder()
                .id(childReplyId)
                .boardId(testBoardId)
                .replyText("부모가 변경된 대댓글")
                .replayer("tester")
                .parentReplyId(rootReply2Id)  // 부모 댓글 ID 변경
                .build();
        
        // when - 테스트 실행
        try {
            replyService.modify(modifiedReply);
            
            // then - 결과 검증
            // 부모 댓글 ID 변경이 허용되는 경우
            ReplyDTO updatedReply = replyService.read(childReplyId);
            log.info("부모 댓글 ID 변경 결과: 원래 부모={}, 변경 후 부모={}", 
                    rootReply1Id, updatedReply.getParentReplyId());
            
            // 부모 댓글 ID가 변경되었는지 확인
            if (rootReply2Id.equals(updatedReply.getParentReplyId())) {
                log.info("부모 댓글 ID 변경이 허용됨");
            } else {
                log.info("부모 댓글 ID 변경이 무시됨");
            }
        } catch (Exception e) {
            // 부모 댓글 ID 변경이 허용되지 않는 경우
            log.info("부모 댓글 ID 변경 시 예외 발생: {}", e.getMessage());
        }
    }
}