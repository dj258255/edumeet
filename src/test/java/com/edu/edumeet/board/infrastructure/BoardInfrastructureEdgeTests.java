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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 게시판 인프라스트럭처 계층에 대한 에지 테스트
 * 경계 조건이나 예외 상황을 테스트
 */
@SpringBootTest
@Log4j2
@Transactional
@ActiveProfiles("test")
public class BoardInfrastructureEdgeTests {

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
    @DisplayName("필수 필드 누락 에지 테스트")
    void missingRequiredFieldsTest() {
        // given
        Board boardWithoutTitle = Board.builder()
                .content("제목 없는 게시글")
                .writer("tester")
                .classId(1L)
                .build();
        
        BoardJpaEntity entityWithoutTitle = BoardJpaEntity.fromDomain(boardWithoutTitle);
        
        // when & then
        assertThatThrownBy(() -> boardJpaRepository.save(entityWithoutTitle))
                .isInstanceOf(DataIntegrityViolationException.class);
        
        log.info("필수 필드(제목) 누락 시 저장 실패 확인");
    }
    
    @Test
    @DisplayName("매우 긴 필드값 에지 테스트")
    void veryLongFieldValuesTest() {
        // given
        String veryLongTitle = "a".repeat(300);  // 컬럼 길이 제한(200)을 초과
        
        Board boardWithLongTitle = Board.builder()
                .title(veryLongTitle)
                .content("긴 제목 테스트")
                .writer("tester")
                .classId(1L)
                .build();
        
        BoardJpaEntity entityWithLongTitle = BoardJpaEntity.fromDomain(boardWithLongTitle);
        
        // when & then
        assertThatThrownBy(() -> boardJpaRepository.save(entityWithLongTitle))
                .isInstanceOf(DataIntegrityViolationException.class);
        
        log.info("매우 긴 제목({}자) 저장 실패 확인", veryLongTitle.length());
    }
    
    @Test
    @DisplayName("존재하지 않는 ID로 조회 에지 테스트")
    void findByNonExistentIdTest() {
        // given
        Long nonExistentId = 99999L;
        
        // when
        Optional<BoardJpaEntity> result = boardJpaRepository.findById(nonExistentId);
        
        // then
        assertThat(result).isEmpty();
        
        log.info("존재하지 않는 ID로 조회 시 빈 Optional 반환 확인: ID={}", nonExistentId);
    }
    
    @Test
    @DisplayName("이미지 세트 최대 크기 에지 테스트")
    void imageSetMaxSizeTest() {
        // given
        Board board = Board.builder()
                .title("이미지 최대 크기 테스트")
                .content("이미지 최대 크기 테스트 내용")
                .writer("tester")
                .classId(1L)
                .categoryId(testCategoryId)
                .build();
        
        // 많은 수의 이미지 추가 (100개)
        for (int i = 0; i < 100; i++) {
            board.addImage(UUID.randomUUID().toString(), "test_image_" + i + ".jpg");
        }
        
        BoardJpaEntity entity = BoardJpaEntity.fromDomain(board);
        
        // when
        BoardJpaEntity savedEntity = boardJpaRepository.save(entity);
        
        // then
        assertThat(savedEntity.getImageSet()).hasSize(100);
        
        log.info("많은 수의 이미지(100개) 저장 성공");
    }
    
    @Test
    @DisplayName("매우 큰 페이지 크기 에지 테스트")
    void veryLargePageSizeTest() {
        // given
        // 여러 게시글 생성
        for (int i = 0; i < 50; i++) {
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
        int veryLargeSize = 1000;  // 매우 큰 페이지 크기
        Pageable pageable = PageRequest.of(0, veryLargeSize, Sort.by("id").descending());
        Page<BoardJpaEntity> result = boardJpaRepository.findAll(pageable);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.getContent().size()).isLessThanOrEqualTo(veryLargeSize);
        assertThat(result.getContent().size()).isGreaterThanOrEqualTo(51);  // 기존 1개 + 추가 50개
        
        log.info("매우 큰 페이지 크기 처리 성공: 요청 크기={}, 실제 결과 크기={}", 
                veryLargeSize, result.getContent().size());
    }
    
    @Test
    @DisplayName("엔티티 변환 에지 테스트 - 순환 참조")
    void entityConversionCircularReferenceTest() {
        // given
        BoardJpaEntity entity = boardJpaRepository.findById(testBoardId).orElseThrow();
        
        // when
        Board board = entity.toModel();
        BoardJpaEntity convertedEntity = BoardJpaEntity.fromDomain(board);
        
        // then
        assertThat(convertedEntity).isNotNull();
        assertThat(convertedEntity.getId()).isEqualTo(testBoardId);
        
        log.info("엔티티-도메인-엔티티 변환 성공: ID={}", testBoardId);
    }
    
    @Test
    @DisplayName("정렬 조건 에지 테스트 - 존재하지 않는 필드")
    void sortByNonExistentFieldTest() {
        // given
        // 여러 게시글 생성
        for (int i = 0; i < 5; i++) {
            Board board = Board.builder()
                    .title("정렬 테스트 " + i)
                    .content("정렬 테스트 내용 " + i)
                    .writer("sortTester")
                    .classId(1L)
                    .categoryId(testCategoryId)
                    .boardType(BoardType.NORMAL)
                    .build();
            
            boardJpaRepository.save(BoardJpaEntity.fromDomain(board));
        }
        
        // when & then
        // 존재하지 않는 필드로 정렬 시도
        assertThatThrownBy(() -> {
            Pageable pageable = PageRequest.of(0, 10, Sort.by("nonExistentField").descending());
            boardJpaRepository.findAll(pageable);
        }).isInstanceOf(Exception.class);
        
        log.info("존재하지 않는 필드로 정렬 시도 실패 확인");
    }
}