package com.edu.edumeet.board.infrastructure;

import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.domain.BoardCategory;
import com.edu.edumeet.board.domain.BoardType;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 게시판 인프라스트럭처 계층에 대한 실행 테스트
 * 정상적인 사용 시나리오를 테스트
 */
@SpringBootTest
@Log4j2
@Transactional
@ActiveProfiles("test")
public class BoardInfrastructureExecutionTests {

    @Autowired
    private BoardJpaRepository boardJpaRepository;
    
    @Autowired
    private BoardCategoryJpaRepository boardCategoryJpaRepository;

    private Long testCategoryId;
    private Long testBoardId;

    @BeforeEach
    void setUp() {
        // 기존 데이터 정리
        boardJpaRepository.deleteAll();
        boardCategoryJpaRepository.deleteAll();
        
        // 테스트용 카테고리 생성
        BoardCategory category = BoardCategory.builder()
                .categoryName("테스트 카테고리")
                .classId(1L)
                .createdBy("tester")
                .build();
        
        BoardCategoryJpaEntity savedCategory = boardCategoryJpaRepository.save(BoardCategoryJpaEntity.fromDomain(category));
        testCategoryId = savedCategory.getId();
        
        // 테스트용 게시글 생성
        Board board = Board.builder()
                .title("테스트 게시글")
                .content("테스트 내용")
                .writer("tester")
                .classId(1L)
                .categoryId(testCategoryId)
                .boardType(BoardType.NORMAL)
                .build();
        
        BoardJpaEntity savedBoard = boardJpaRepository.save(BoardJpaEntity.fromDomain(board));
        testBoardId = savedBoard.getId();
        
        log.info("테스트 준비 완료: 카테고리 ID={}, 게시글 ID={}", testCategoryId, testBoardId);
    }
    
    @Test
    @DisplayName("JPA 엔티티 저장 실행 테스트")
    void saveBoardJpaEntityTest() {
        // given
        Board board = Board.builder()
                .title("새 게시글")
                .content("새 게시글 내용")
                .writer("newUser")
                .classId(1L)
                .categoryId(testCategoryId)
                .boardType(BoardType.NORMAL)
                .build();
        
        BoardJpaEntity boardJpaEntity = BoardJpaEntity.fromDomain(board);
        
        // when
        BoardJpaEntity savedEntity = boardJpaRepository.save(boardJpaEntity);
        
        // then
        assertThat(savedEntity).isNotNull();
        assertThat(savedEntity.getId()).isNotNull();
        assertThat(savedEntity.getTitle()).isEqualTo("새 게시글");
        
        log.info("게시글 JPA 엔티티 저장 성공: ID={}", savedEntity.getId());
    }
    
    @Test
    @DisplayName("JPA 엔티티 조회 실행 테스트")
    void findBoardJpaEntityByIdTest() {
        // when
        Optional<BoardJpaEntity> result = boardJpaRepository.findById(testBoardId);
        
        // then
        assertThat(result).isPresent();
        BoardJpaEntity entity = result.get();
        assertThat(entity.getTitle()).isEqualTo("테스트 게시글");
        
        log.info("게시글 JPA 엔티티 조회 성공: ID={}, 제목={}", entity.getId(), entity.getTitle());
    }
    
    @Test
    @DisplayName("JPA 엔티티 수정 실행 테스트")
    void updateBoardJpaEntityTest() {
        // given
        BoardJpaEntity entity = boardJpaRepository.findById(testBoardId).orElseThrow();
        
        // 도메인 객체로 변환 후 수정
        Board board = entity.toModel();
        Board updatedBoard = board.change("수정된 제목", "수정된 내용");
        
        // 다시 JPA 엔티티로 변환
        entity.updateFromDomain(updatedBoard);
        
        // when
        BoardJpaEntity updatedEntity = boardJpaRepository.save(entity);
        
        // then
        assertThat(updatedEntity.getTitle()).isEqualTo("수정된 제목");
        assertThat(updatedEntity.getContent()).isEqualTo("수정된 내용");
        
        log.info("게시글 JPA 엔티티 수정 성공: ID={}, 제목={}", updatedEntity.getId(), updatedEntity.getTitle());
    }
    
    @Test
    @DisplayName("JPA 엔티티 삭제 실행 테스트")
    void deleteBoardJpaEntityTest() {
        // when
        boardJpaRepository.deleteById(testBoardId);
        
        // then
        Optional<BoardJpaEntity> result = boardJpaRepository.findById(testBoardId);
        assertThat(result).isEmpty();
        
        log.info("게시글 JPA 엔티티 삭제 성공: ID={}", testBoardId);
    }
    
    @Test
    @DisplayName("JPA 엔티티 페이징 조회 실행 테스트")
    void findBoardJpaEntityWithPagingTest() {
        // given
        // 추가 게시글 생성
        for (int i = 0; i < 20; i++) {
            Board board = Board.builder()
                    .title("페이징 테스트 " + i)
                    .content("페이징 테스트 내용 " + i)
                    .writer("pageTester")
                    .classId(1L)
                    .categoryId(testCategoryId)
                    .boardType(BoardType.NORMAL)
                    .build();
            
            boardJpaRepository.save(BoardJpaEntity.fromDomain(board));
        }
        
        // when
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id").descending());
        Page<BoardJpaEntity> result = boardJpaRepository.findAll(pageable);
        
        // then
        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getContent().size()).isEqualTo(10);
        assertThat(result.getTotalElements()).isGreaterThanOrEqualTo(21); // 기존 1개 + 추가 20개
        
        log.info("게시글 JPA 엔티티 페이징 조회 성공: 총 {}개, 현재 페이지 {}개", 
                result.getTotalElements(), result.getContent().size());
    }
    
    @Test
    @DisplayName("JPA 엔티티 이미지 추가 실행 테스트")
    void addImageToBoardJpaEntityTest() {
        // given
        BoardJpaEntity entity = boardJpaRepository.findById(testBoardId).orElseThrow();
        Board board = entity.toModel();
        
        // 도메인 객체에 이미지 추가
        String uuid = UUID.randomUUID().toString();
        String fileName = "test_image.jpg";
        board.addImage(uuid, fileName);
        
        // 엔티티 업데이트
        entity.updateFromDomain(board);
        
        // when
        BoardJpaEntity savedEntity = boardJpaRepository.save(entity);
        
        // then
        assertThat(savedEntity.getImageSet()).isNotEmpty();
        assertThat(savedEntity.getImageSet().size()).isEqualTo(1);
        
        BoardImageJpaEntity imageEntity = savedEntity.getImageSet().iterator().next();
        assertThat(imageEntity.getUuid()).isEqualTo(uuid);
        assertThat(imageEntity.getFilename()).isEqualTo(fileName);
        
        log.info("게시글 JPA 엔티티 이미지 추가 성공: 게시글 ID={}, 이미지 UUID={}", 
                savedEntity.getId(), imageEntity.getUuid());
    }
}