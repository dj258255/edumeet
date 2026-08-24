package com.edu.edumeet.integration.reply;

import com.edu.edumeet.reply.service.ReplyService;
import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.domain.BoardJpaEntity;
import com.edu.edumeet.board.repository.BoardJpaRepository;
import com.edu.edumeet.board.dto.PageRequestDTO;
import com.edu.edumeet.board.dto.PageResponseDTO;
import com.edu.edumeet.reply.repository.ReplyRepository;
import com.edu.edumeet.reply.dto.ReplyDTO;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 댓글 기능에 대한 실행 테스트
 * 정상적인 사용 시나리오를 테스트합니다.
 */
@SpringBootTest
@Log4j2
@Transactional
@ActiveProfiles("test")
public class ReplyExecutionTests {

    @Autowired
    private ReplyService replyService;

    @Autowired
    private BoardJpaRepository boardJpaRepository;
    
    @Autowired
    private ReplyRepository replyJpaRepository;

    private Long testBoardId;
    private Long testReplyId;
    private Long secondBoardId;

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
        
        // 두 번째 테스트용 게시글 생성
        Board secondBoard = Board.builder()
                .title("두 번째 테스트용 게시글")
                .content("두 번째 테스트용 내용")
                .writer("tester2")
                .classId(1L)
                .build();
        
        BoardJpaEntity savedSecondBoard = boardJpaRepository.save(BoardJpaEntity.fromDomain(secondBoard));
        secondBoardId = savedSecondBoard.getId();
        
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
    @DisplayName("댓글 등록 테스트")
    void registerReplyTest() {
        // given - 테스트 데이터 준비
        ReplyDTO replyDTO = ReplyDTO.builder()
                .replyText("새 댓글")
                .replayer("newUser")
                .boardId(testBoardId)
                .build();
        
        // when - 테스트 실행
        Long newReplyId = replyService.register(replyDTO);
        
        // then - 결과 검증
        ReplyDTO savedReply = replyService.read(newReplyId);
        assertThat(savedReply).isNotNull();
        assertThat(savedReply.getReplyText()).isEqualTo("새 댓글");
        assertThat(savedReply.getReplayer()).isEqualTo("newUser");
        
        log.info("댓글 등록 성공: ID={}", newReplyId);
    }
    
    @Test
    @DisplayName("댓글 수정 테스트")
    void modifyReplyTest() {
        // given - 테스트 데이터 준비
        ReplyDTO replyDTO = replyService.read(testReplyId);
        replyDTO.setReplyText("수정된 댓글");
        
        // when - 테스트 실행
        replyService.modify(replyDTO);
        
        // then - 결과 검증
        ReplyDTO modifiedReply = replyService.read(testReplyId);
        assertThat(modifiedReply.getReplyText()).isEqualTo("수정된 댓글");
        
        log.info("댓글 수정 성공: ID={}", testReplyId);
    }
    
    @Test
    @DisplayName("댓글 삭제 테스트")
    void removeReplyTest() {
        // given - 테스트 데이터 준비
        ReplyDTO replyDTO = replyService.read(testReplyId);
        assertThat(replyDTO).isNotNull();
        
        // when - 테스트 실행
        replyService.remove(testReplyId);
        
        // then - 결과 검증
        ReplyDTO deletedReply = replyService.read(testReplyId);
        assertThat(deletedReply).isNull();
        
        log.info("댓글 삭제 성공: ID={}", testReplyId);
    }
    
    @Test
    @DisplayName("게시글별 댓글 목록 조회 테스트")
    void getListOfBoardTest() {
        // given - 테스트 데이터 준비
        // 추가 댓글 생성
        for (int i = 0; i < 5; i++) {
            ReplyDTO replyDTO = ReplyDTO.builder()
                    .replyText("목록 테스트 댓글 " + i)
                    .replayer("listTester")
                    .boardId(testBoardId)
                    .build();
            replyService.register(replyDTO);
        }
        
        // when - 테스트 실행
        PageRequestDTO pageRequestDTO = PageRequestDTO.builder()
                .page(1)
                .size(10)
                .build();
        
        PageResponseDTO<ReplyDTO> responseDTO = replyService.getListOfBoard(testBoardId, pageRequestDTO);
        
        // then - 결과 검증
        assertThat(responseDTO.getDtoList()).isNotEmpty();
        assertThat(responseDTO.getDtoList().size()).isGreaterThanOrEqualTo(6); // 기존 1개 + 추가 5개
        
        log.info("게시글별 댓글 목록 조회 성공: 게시글 ID={}, 댓글 수={}", 
                testBoardId, responseDTO.getDtoList().size());
    }
    
    @Test
    @DisplayName("대댓글 등록 테스트")
    void registerChildReplyTest() {
        // given - 테스트 데이터 준비
        ReplyDTO childReply = ReplyDTO.builder()
                .replyText("대댓글")
                .replayer("childReplyer")
                .boardId(testBoardId)
                .parentReplyId(testReplyId)
                .build();
        
        // when - 테스트 실행
        Long childReplyId = replyService.register(childReply);
        
        // then - 결과 검증
        ReplyDTO savedChildReply = replyService.read(childReplyId);
        assertThat(savedChildReply).isNotNull();
        assertThat(savedChildReply.getParentReplyId()).isEqualTo(testReplyId);
        
        log.info("대댓글 등록 성공: ID={}, 부모 댓글 ID={}", childReplyId, testReplyId);
    }
    
    @Test
    @DisplayName("계층형 댓글 목록 조회 테스트")
    void getHierarchicalListOfBoardTest() {
        // given - 테스트 데이터 준비
        // 최상위 댓글 3개 생성
        Long[] rootReplyIds = new Long[3];
        for (int i = 0; i < 3; i++) {
            ReplyDTO rootReply = ReplyDTO.builder()
                    .boardId(testBoardId)
                    .replyText("최상위 댓글 " + i)
                    .replayer("user" + i)
                    .build();
            rootReplyIds[i] = replyService.register(rootReply);
        }
        
        // 첫 번째 최상위 댓글에 대댓글 2개 추가
        replyService.register(ReplyDTO.builder()
                .boardId(testBoardId)
                .replyText("첫 번째 댓글의 첫 번째 대댓글")
                .replayer("child1")
                .parentReplyId(rootReplyIds[0])
                .build());
        
        replyService.register(ReplyDTO.builder()
                .boardId(testBoardId)
                .replyText("첫 번째 댓글의 두 번째 대댓글")
                .replayer("child2")
                .parentReplyId(rootReplyIds[0])
                .build());
        
        // 두 번째 최상위 댓글에 대댓글 1개 추가
        replyService.register(ReplyDTO.builder()
                .boardId(testBoardId)
                .replyText("두 번째 댓글의 대댓글")
                .replayer("child3")
                .parentReplyId(rootReplyIds[1])
                .build());
        
        // when - 테스트 실행
        PageRequestDTO pageRequest = PageRequestDTO.builder()
                .page(1)
                .size(10)
                .build();
        
        PageResponseDTO<ReplyDTO> response = replyService.getHierarchicalListOfBoard(testBoardId, pageRequest);
        
        // then - 결과 검증
        assertThat(response.getDtoList()).isNotEmpty();
        
        // 최상위 댓글 필터링
        List<ReplyDTO> topLevelReplies = response.getDtoList().stream()
                .filter(reply -> reply.getDepth() == 0)
                .collect(Collectors.toList());
        
        assertThat(topLevelReplies.size()).isGreaterThanOrEqualTo(3);
        
        // 첫 번째 최상위 댓글의 대댓글 확인
        ReplyDTO firstRootReply = findReplyById(response.getDtoList(), rootReplyIds[0]);
        if (firstRootReply.hasChildren()) {
            assertThat(firstRootReply.getChildren().size()).isEqualTo(2);
        } else {
            List<ReplyDTO> childReplies = replyService.getChildReplies(rootReplyIds[0]);
            assertThat(childReplies.size()).isEqualTo(2);
        }
        
        log.info("계층형 댓글 목록 조회 성공: 게시글 ID={}, 최상위 댓글 수={}", 
                testBoardId, topLevelReplies.size());
    }
    
    @Test
    @DisplayName("대댓글 수정 테스트")
    void modifyChildReplyTest() {
        // given - 테스트 데이터 준비
        // 부모 댓글 생성
        ReplyDTO rootReply = ReplyDTO.builder()
                .boardId(testBoardId)
                .replyText("최상위 댓글")
                .replayer("tester")
                .build();
        Long rootReplyId = replyService.register(rootReply);
        
        // 대댓글 생성
        ReplyDTO childReply = ReplyDTO.builder()
                .boardId(testBoardId)
                .replyText("원본 대댓글")
                .replayer("tester")
                .parentReplyId(rootReplyId)
                .build();
        Long childReplyId = replyService.register(childReply);
        
        // when - 테스트 실행
        // 대댓글 수정
        ReplyDTO updateReply = ReplyDTO.builder()
                .id(childReplyId)
                .boardId(testBoardId)
                .replyText("수정된 대댓글")
                .replayer("tester")
                .parentReplyId(rootReplyId)
                .build();
        
        replyService.modify(updateReply);
        
        // then - 결과 검증
        ReplyDTO modifiedReply = replyService.read(childReplyId);
        assertThat(modifiedReply).isNotNull();
        assertThat(modifiedReply.getReplyText()).isEqualTo("수정된 대댓글");
        assertThat(modifiedReply.getParentReplyId()).isEqualTo(rootReplyId);
        
        log.info("대댓글 수정 성공: ID={}, 부모 댓글 ID={}", childReplyId, rootReplyId);
    }
    
    @Test
    @DisplayName("댓글 삭제 시 하위 댓글 처리 테스트")
    void deleteWithChildRepliesTest() {
        // given - 테스트 데이터 준비
        // 부모 댓글 생성
        ReplyDTO rootReply = ReplyDTO.builder()
                .boardId(testBoardId)
                .replyText("삭제될 최상위 댓글")
                .replayer("tester")
                .build();
        Long rootReplyId = replyService.register(rootReply);
        
        // 대댓글 생성
        ReplyDTO childReply = ReplyDTO.builder()
                .boardId(testBoardId)
                .replyText("함께 삭제될 대댓글")
                .replayer("tester")
                .parentReplyId(rootReplyId)
                .build();
        Long childReplyId = replyService.register(childReply);
        
        // when - 테스트 실행
        replyService.remove(rootReplyId);
        
        // then - 결과 검증
        assertThat(replyService.read(rootReplyId)).isNull();
        assertThat(replyService.read(childReplyId)).isNull();
        
        log.info("계층형 삭제 검증 완료: 부모 댓글 삭제 시 자식 댓글도 함께 삭제됨");
    }
    
    @Test
    @DisplayName("빈 게시글의 계층형 댓글 목록 조회 테스트")
    void emptyHierarchicalReplyListTest() {
        // given - 테스트 데이터 준비
        // 댓글이 없는 게시글 사용
        
        // when - 테스트 실행
        PageRequestDTO pageRequest = PageRequestDTO.builder()
                .page(1)
                .size(10)
                .build();
        PageResponseDTO<ReplyDTO> result = replyService.getHierarchicalListOfBoard(secondBoardId, pageRequest);
        
        // then - 결과 검증
        assertThat(result.getDtoList()).isEmpty();
        assertThat(result.getTotal()).isEqualTo(0);
        assertThat(result.isPrev()).isFalse();
        assertThat(result.isNext()).isFalse();
        
        log.info("빈 게시글 계층형 댓글 목록 검증 완료");
    }
    
    // 헬퍼 메서드
    private ReplyDTO findReplyById(List<ReplyDTO> replies, Long id) {
        return replies.stream()
                .filter(reply -> reply.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
}