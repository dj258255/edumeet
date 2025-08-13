package com.edu.edumeet.integration.board.infrastructure;

import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.domain.BoardCategory;
import com.edu.edumeet.board.domain.BoardType;
import com.edu.edumeet.board.infrastructure.*;
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
 * 게시판 인프라스트럭처 레이어의 에지 케이스(경계 조건)를 테스트하는 클래스
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

    /**
     * 각 테스트 전에 실행되는 설정 메소드
     * 테스트용 카테고리와 게시글을 생성합니다.
     */
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
                .build();
        
        BoardJpaEntity savedBoard = boardJpaRepository.save(BoardJpaEntity.fromDomain(board));
        testBoardId = savedBoard.getId();
        
        log.info("테스트 준비 완료: 카테고리 ID={}, 게시글 ID={}", testCategoryId, testBoardId);
    }

    /**
     * 필수 필드가 누락된 경우 예외가 발생하는지 테스트합니다.
     */
    @Test
    @DisplayName("필수 필드 누락 에지 테스트")
    void missingRequiredFieldsTest() {
        // given
        BoardJpaEntity invalidEntity = BoardJpaEntity.builder()
                // title 필드 누락
                .content("내용만 있는 게시글")
                .writer("edgeTester")
                .classId(1L)
                .build();
        
        // when & then
        assertThatThrownBy(() -> boardJpaRepository.save(invalidEntity))
                .isInstanceOf(Exception.class);
        
        log.info("필수 필드 누락 예외 발생 확인");
    }
    
    /**
     * 매우 긴 필드값 처리를 테스트합니다.
     */
    @Test
    @DisplayName("매우 긴 필드값 에지 테스트")
    void veryLongFieldValuesTest() {
        // given
        String longTitle = "a".repeat(1000);  // 매우 긴 제목
        String longContent = "b".repeat(10000);  // 매우 긴 내용
        
        BoardJpaEntity entity = BoardJpaEntity.builder()
                .title(longTitle)
                .content(longContent)
                .writer("edgeTester")
                .classId(1L)
                .categoryId(testCategoryId)
                .build();
        
        // when & then - 데이터베이스 컬럼 길이 제한에 따라 예외 발생 가능성 있음
        assertThatThrownBy(() -> boardJpaRepository.save(entity))
                .isInstanceOf(Exception.class);
        
        log.info("매우 긴 필드값 처리 예외 발생 확인: 제목 길이={}, 내용 길이={}", 
                longTitle.length(), longContent.length());
    }
    
    /**
     * 존재하지 않는 ID로 조회 시 결과가 없는지 테스트합니다.
     */
    @Test
    @DisplayName("존재하지 않는 ID 조회 에지 테스트")
    void findByNonExistentIdTest() {
        // given
        Long nonExistentId = 99999L;
        
        // when
        Optional<BoardJpaEntity> result = boardJpaRepository.findById(nonExistentId);
        
        // then
        assertThat(result).isEmpty();
        
        log.info("존재하지 않는 ID 조회 결과 없음 확인: ID={}", nonExistentId);
    }
    
    /**
     * 이미지 세트 최대 크기 처리를 테스트합니다.
     */
    @Test
    @DisplayName("이미지 세트 최대 크기 에지 테스트")
    void imageSetMaxSizeTest() {
        // given
        BoardJpaEntity entity = boardJpaRepository.findById(testBoardId).orElseThrow();
        
        // 많은 이미지 추가
        for (int i = 0; i < 20; i++) {
            String uuid = UUID.randomUUID().toString();
            String filename = "test_image_" + i + ".jpg";
            
            BoardFileUploadJpaEntity imageEntity = BoardFileUploadJpaEntity.builder()
                    .uuid(uuid)
                    .fileName(filename)
                    .ord(i)
                    .boardJpaEntity(entity)
                    .img(true)
                    .build();
            
            entity.getImageSet().add(imageEntity);
        }
        
        // when
        boardJpaRepository.save(entity);
        
        // then
        BoardJpaEntity updatedEntity = boardJpaRepository.findById(testBoardId).orElseThrow();
        assertThat(updatedEntity.getImageSet().size()).isEqualTo(20);
        
        log.info("이미지 세트 최대 크기 처리 성공: 이미지 수={}", updatedEntity.getImageSet().size());
    }
    
    /**
     * 매우 큰 페이지 크기 처리를 테스트합니다.
     */
    @Test
    @DisplayName("매우 큰 페이지 크기 에지 테스트")
    void veryLargePageSizeTest() {
        // given
        // 많은 게시글 생성
        for (int i = 0; i < 100; i++) {
            Board board = Board.builder()
                    .title("페이지 테스트 " + i)
                    .content("페이지 테스트 내용 " + i)
                    .writer("pageTester")
                    .classId(1L)
                    .categoryId(testCategoryId)
                    .build();
            
            boardJpaRepository.save(BoardJpaEntity.fromDomain(board));
        }
        
        // when
        Pageable pageable = PageRequest.of(0, 1000);  // 매우 큰 페이지 크기
        Page<BoardJpaEntity> result = boardJpaRepository.findAll(pageable);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.getContent().size()).isGreaterThanOrEqualTo(101);  // 기존 1개 + 추가 100개
        
        log.info("매우 큰 페이지 크기 처리 성공: 요청 크기=1000, 실제 결과 크기={}", 
                result.getContent().size());
    }
    
    /**
     * 엔티티 변환 시 순환 참조 처리를 테스트합니다.
     */
    @Test
    @DisplayName("엔티티 변환 순환 참조 에지 테스트")
    void entityConversionCircularReferenceTest() {
        // given
        BoardJpaEntity entity = boardJpaRepository.findById(testBoardId).orElseThrow();
        
        // 이미지 추가
        String uuid = UUID.randomUUID().toString();
        BoardFileUploadJpaEntity imageEntity = BoardFileUploadJpaEntity.builder()
                .uuid(uuid)
                .fileName("test_image.jpg")
                .ord(1)
                .boardJpaEntity(entity)
                .img(true)
                .build();
        
        entity.getImageSet().add(imageEntity);
        boardJpaRepository.save(entity);
        
        // when & then - toString() 호출 시 순환 참조로 인한 StackOverflowError가 발생하지 않아야 함
        BoardJpaEntity savedEntity = boardJpaRepository.findById(testBoardId).orElseThrow();
        String entityString = savedEntity.toString();
        
        assertThat(entityString).isNotNull();
        
        log.info("엔티티 변환 순환 참조 처리 성공");
    }
    
    /**
     * 존재하지 않는 필드로 정렬 시 예외가 발생하는지 테스트합니다.
     */
    @Test
    @DisplayName("존재하지 않는 필드로 정렬 에지 테스트")
    void sortByNonExistentFieldTest() {
        // given
        String nonExistentField = "nonExistentField";
        
        // when & then
        assertThatThrownBy(() -> {
            Pageable pageable = PageRequest.of(0, 10, Sort.by(nonExistentField));
            boardJpaRepository.findAll(pageable);
        }).isInstanceOf(Exception.class);
        
        log.info("존재하지 않는 필드로 정렬 예외 발생 확인: 필드={}", nonExistentField);
    }
}