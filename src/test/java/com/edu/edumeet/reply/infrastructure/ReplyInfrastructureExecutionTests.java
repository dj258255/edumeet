package com.edu.edumeet.reply.infrastructure;

import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.infrastructure.BoardJpaEntity;
import com.edu.edumeet.board.infrastructure.BoardJpaRepository;
import com.edu.edumeet.reply.domain.Reply;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 댓글 인프라스트럭처 계층에 대한 실행 테스트
 * 정상적인 사용 시나리오를 테스트
 */
@SpringBootTest
@Log4j2
@Transactional
@ActiveProfiles("test")
public class ReplyInfrastructureExecutionTests {

    @Autowired
    private ReplyJpaRepository replyJpaRepository;

    @Autowired
    private BoardJpaRepository boardJpaRepository;

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
        Reply reply = Reply.builder()
                .replyText("테스트 댓글")
                .replayer("tester")
                .boardId(testBoardId)
                .build();
        
        ReplyJpaEntity savedReply = replyJpaRepository.save(ReplyJpaEntity.fromDomain(reply));
        testReplyId = savedReply.getId();
        
        log.info("테스트 준비 완료: 게시글 ID={}, 댓글 ID={}", testBoardId, testReplyId);
    }
    
    @Test
    @DisplayName("JPA 엔티티 저장 실행 테스트")
    void saveReplyJpaEntityTest() {
        // given
        Reply reply = Reply.builder()
                .replyText("새 댓글")
                .replayer("newUser")
                .boardId(testBoardId)
                .build();
        
        ReplyJpaEntity entity = ReplyJpaEntity.fromDomain(reply);
        
        // when
        ReplyJpaEntity savedEntity = replyJpaRepository.save(entity);
        
        // then
        assertThat(savedEntity).isNotNull();
        assertThat(savedEntity.getId()).isNotNull();
        assertThat(savedEntity.getReplyText()).isEqualTo("새 댓글");
        
        log.info("댓글 JPA 엔티티 저장 성공: ID={}", savedEntity.getId());
    }
    
    @Test
    @DisplayName("JPA 엔티티 조회 실행 테스트")
    void findReplyJpaEntityByIdTest() {
        // when
        Optional<ReplyJpaEntity> result = replyJpaRepository.findById(testReplyId);
        
        // then
        assertThat(result).isPresent();
        ReplyJpaEntity entity = result.get();
        assertThat(entity.getReplyText()).isEqualTo("테스트 댓글");
        
        log.info("댓글 JPA 엔티티 조회 성공: ID={}, 내용={}", entity.getId(), entity.getReplyText());
    }
    
    @Test
    @DisplayName("삭제되지 않은 JPA 엔티티 조회 실행 테스트")
    void findByIdNotDeletedTest() {
        // when
        Optional<ReplyJpaEntity> result = replyJpaRepository.findByIdNotDeleted(testReplyId);
        
        // then
        assertThat(result).isPresent();
        ReplyJpaEntity entity = result.get();
        assertThat(entity.getReplyText()).isEqualTo("테스트 댓글");
        
        log.info("삭제되지 않은 댓글 JPA 엔티티 조회 성공: ID={}", entity.getId());
    }
    
    @Test
    @DisplayName("게시글별 댓글 목록 조회 실행 테스트")
    void listOfBoardTest() {
        // given
        // 추가 댓글 생성
        for (int i = 0; i < 5; i++) {
            Reply reply = Reply.builder()
                    .replyText("목록 테스트 댓글 " + i)
                    .replayer("listTester")
                    .boardId(testBoardId)
                    .build();
            
            replyJpaRepository.save(ReplyJpaEntity.fromDomain(reply));
        }
        
        // when
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id").descending());
        Page<ReplyJpaEntity> result = replyJpaRepository.listOfBoard(testBoardId, pageable);
        
        // then
        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getContent().size()).isGreaterThanOrEqualTo(6); // 기존 1개 + 추가 5개
        
        log.info("게시글별 댓글 JPA 엔티티 목록 조회 성공: 게시글 ID={}, 댓글 수={}", 
                testBoardId, result.getContent().size());
    }
    
    @Test
    @DisplayName("최상위 댓글만 조회 실행 테스트")
    void listOfBoardRootRepliesTest() {
        // given
        // 대댓글 생성
        Reply childReply = Reply.builder()
                .replyText("대댓글")
                .replayer("childReplyer")
                .boardId(testBoardId)
                .parentReplyId(testReplyId)
                .depth(1)
                .build();
        
        replyJpaRepository.save(ReplyJpaEntity.fromDomain(childReply));
        
        // 추가 최상위 댓글 생성
        for (int i = 0; i < 3; i++) {
            Reply reply = Reply.builder()
                    .replyText("최상위 댓글 " + i)
                    .replayer("rootReplyer")
                    .boardId(testBoardId)
                    .build();
            
            replyJpaRepository.save(ReplyJpaEntity.fromDomain(reply));
        }
        
        // when
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id").descending());
        Page<ReplyJpaEntity> result = replyJpaRepository.listOfBoardRootReplies(testBoardId, pageable);
        
        // then
        assertThat(result.getContent()).isNotEmpty();
        // 최상위 댓글만 조회되어야 함 (대댓글 제외)
        assertThat(result.getContent().size()).isEqualTo(4); // 기존 1개 + 추가 3개
        assertThat(result.getContent()).allMatch(reply -> reply.getParentReply() == null);
        
        log.info("최상위 댓글 JPA 엔티티 목록 조회 성공: 게시글 ID={}, 최상위 댓글 수={}", 
                testBoardId, result.getContent().size());
    }
    
    @Test
    @DisplayName("부모 댓글별 대댓글 목록 조회 실행 테스트")
    void findByParentReplyIdTest() {
        // given
        // 대댓글 생성
        for (int i = 0; i < 3; i++) {
            Reply childReply = Reply.builder()
                    .replyText("대댓글 " + i)
                    .replayer("childReplyer")
                    .boardId(testBoardId)
                    .parentReplyId(testReplyId)
                    .depth(1)
                    .build();
            
            replyJpaRepository.save(ReplyJpaEntity.fromDomain(childReply));
        }
        
        // when
        List<ReplyJpaEntity> childReplies = replyJpaRepository.findByParentReplyId(testReplyId);
        
        // then
        assertThat(childReplies).isNotEmpty();
        assertThat(childReplies.size()).isEqualTo(3);
        assertThat(childReplies).allMatch(reply -> 
                reply.getParentReply() != null && 
                reply.getParentReply().getId().equals(testReplyId));
        
        log.info("부모 댓글별 대댓글 JPA 엔티티 목록 조회 성공: 부모 ID={}, 대댓글 수={}", 
                testReplyId, childReplies.size());
    }
    
    @Test
    @DisplayName("대댓글 존재 여부 확인 실행 테스트")
    void hasChildRepliesTest() {
        // given
        // 대댓글 생성
        Reply childReply = Reply.builder()
                .replyText("대댓글")
                .replayer("childReplyer")
                .boardId(testBoardId)
                .parentReplyId(testReplyId)
                .depth(1)
                .build();
        
        replyJpaRepository.save(ReplyJpaEntity.fromDomain(childReply));
        
        // when
        boolean hasChildren = replyJpaRepository.hasChildReplies(testReplyId);
        
        // then
        assertThat(hasChildren).isTrue();
        
        log.info("대댓글 존재 여부 확인 성공: 부모 ID={}, 대댓글 존재={}", testReplyId, hasChildren);
    }
    
    @Test
    @DisplayName("도메인 변환 실행 테스트")
    void domainConversionTest() {
        // given
        ReplyJpaEntity entity = replyJpaRepository.findById(testReplyId).orElseThrow();
        
        // when
        Reply reply = entity.toDomain();
        ReplyJpaEntity convertedEntity = ReplyJpaEntity.fromDomain(reply);
        
        // then
        assertThat(convertedEntity.getId()).isEqualTo(entity.getId());
        assertThat(convertedEntity.getReplyText()).isEqualTo(entity.getReplyText());
        assertThat(convertedEntity.getReplayer()).isEqualTo(entity.getReplayer());
        
        log.info("도메인 변환 성공: 엔티티 ID={}, 도메인 ID={}", entity.getId(), reply.getId());
    }
}