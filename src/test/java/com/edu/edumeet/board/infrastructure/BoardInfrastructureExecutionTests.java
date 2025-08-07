package com.edu.edumeet.board.infrastructure;

import com.edu.edumeet.board.application.BoardSearchRepository;
import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.domain.BoardCategory;
import com.edu.edumeet.board.domain.BoardType;
import com.edu.edumeet.board.presentation.dto.BoardListAllDTO;
import com.edu.edumeet.board.presentation.dto.BoardListReplyCountDTO;
import com.edu.edumeet.reply.infrastructure.ReplyJpaRepository;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
 * 게시판 인프라스트럭처 레이어의 정상적인 사용 시나리오를 테스트하는 클래스
 */
@SpringBootTest
@Log4j2
@Transactional
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BoardInfrastructureExecutionTests {

    @Autowired
    private BoardJpaRepository boardJpaRepository;
    
    @Autowired
    private BoardCategoryJpaRepository boardCategoryJpaRepository;
    
    @Autowired
    private BoardSearchRepository boardSearchRepository;
    
    @Autowired
    private ReplyJpaRepository replyJpaRepository;

    private Long testCategoryId;
    private Long testBoardId;

    /**
     * 테스트에 사용할 더미 데이터를 생성합니다.
     */
    @BeforeAll
    public void 테스트_더미데이터_생성() {
        // 기존 데이터 삭제
        replyJpaRepository.deleteAll();
        boardJpaRepository.deleteAll();

        log.info("테스트 데이터 생성 시작");

        for (int i = 1; i <= 100; i++) {
            Board.BoardBuilder boardBuilder = Board.builder()
                    .title("title..." + i)
                    .content("content..." + i)
                    .writer("user" + (i % 10))
                    .classId(1L);

            Board board = boardBuilder.build();

            // 도메인로직으로 이미지 추가
            for (int j = 0; j < 3; j++) {
                if (i % 5 == 0) {
                    continue;
                }
                board.addImage(UUID.randomUUID().toString(), "file" + i + ".jpg");
            }

            // 도메인 모델을 jpa 엔티티로 변환 후 저장
            BoardJpaEntity savedEntity = boardJpaRepository.save(BoardJpaEntity.fromDomain(board));

            if (i == 1) {
                testBoardId = savedEntity.getId();
            }
        }

        // 클래스 2 데이터 추가 (검색 테스트용)
        for (int i = 101; i <= 110; i++) {
            Board board = Board.builder()
                    .title("클래스2_title..." + i)
                    .content("클래스2_content..." + i)
                    .writer("클래스2_user" + (i % 5))
                    .classId(2L)
                    .build();

            // 이미지도 추가
            if (i % 3 != 0) {
                board.addImage(UUID.randomUUID().toString(), "class2_file" + i + ".jpg");
            }

            boardJpaRepository.save(BoardJpaEntity.fromDomain(board));
        }

        log.info("테스트 데이터 생성 완료 : 첫 번째 게시글 ID : " + testBoardId);
        
        // 테스트용 카테고리 생성
        BoardCategory category = BoardCategory.builder()
                .categoryName("테스트 카테고리")
                .classId(1L)
                .createdBy("tester")
                .build();
        
        BoardCategoryJpaEntity savedCategory = boardCategoryJpaRepository.save(BoardCategoryJpaEntity.fromDomain(category));
        testCategoryId = savedCategory.getId();
    }

    /**
     * 게시글 JPA 엔티티 저장 기능을 테스트합니다.
     */
    @Test
    @DisplayName("게시글 JPA 엔티티 저장 테스트")
    void saveBoardJpaEntityTest() {
        // given
        Board newBoard = Board.builder()
                .title("새 게시글")
                .content("새 게시글 내용")
                .writer("newUser")
                .classId(1L)
                .categoryId(testCategoryId)
                .build();
        
        BoardJpaEntity boardJpaEntity = BoardJpaEntity.fromDomain(newBoard);
        
        // when
        BoardJpaEntity savedEntity = boardJpaRepository.save(boardJpaEntity);
        
        // then
        assertThat(savedEntity).isNotNull();
        assertThat(savedEntity.getId()).isNotNull();
        assertThat(savedEntity.getTitle()).isEqualTo("새 게시글");
        assertThat(savedEntity.getContent()).isEqualTo("새 게시글 내용");
        assertThat(savedEntity.getWriter()).isEqualTo("newUser");
        
        log.info("게시글 JPA 엔티티 저장 성공: ID={}", savedEntity.getId());
    }
    
    /**
     * 게시글 JPA 엔티티 조회 기능을 테스트합니다.
     */
    @Test
    @DisplayName("게시글 JPA 엔티티 조회 테스트")
    void findBoardJpaEntityByIdTest() {
        // when
        Optional<BoardJpaEntity> result = boardJpaRepository.findById(testBoardId);
        
        // then
        assertThat(result).isPresent();
        BoardJpaEntity entity = result.get();
        assertThat(entity.getTitle()).contains("title");
        
        log.info("게시글 JPA 엔티티 조회 성공: ID={}, 제목={}", entity.getId(), entity.getTitle());
    }
    
    /**
     * 게시글 JPA 엔티티 수정 기능을 테스트합니다.
     */
    @Test
    @DisplayName("게시글 JPA 엔티티 수정 테스트")
    void updateBoardJpaEntityTest() {
        // given
        BoardJpaEntity entity = boardJpaRepository.findById(testBoardId).orElseThrow();
        String updatedTitle = "수정된 제목";
        String updatedContent = "수정된 내용";
        
        // when
        entity.setTitle(updatedTitle);
        entity.setContent(updatedContent);
        boardJpaRepository.save(entity);
        
        // then
        BoardJpaEntity updatedEntity = boardJpaRepository.findById(testBoardId).orElseThrow();
        assertThat(updatedEntity.getTitle()).isEqualTo(updatedTitle);
        assertThat(updatedEntity.getContent()).isEqualTo(updatedContent);
        
        log.info("게시글 JPA 엔티티 수정 성공: ID={}, 제목={}", updatedEntity.getId(), updatedEntity.getTitle());
    }
    
    /**
     * 게시글 JPA 엔티티 삭제 기능을 테스트합니다.
     */
    @Test
    @DisplayName("게시글 JPA 엔티티 삭제 테스트")
    void deleteBoardJpaEntityTest() {
        // given
        Long boardIdToDelete = boardJpaRepository.findAll().get(10).getId(); // 다른 게시글 ID 사용
        
        // when
        boardJpaRepository.deleteById(boardIdToDelete);
        
        // then
        Optional<BoardJpaEntity> result = boardJpaRepository.findById(boardIdToDelete);
        assertThat(result).isEmpty();
        
        log.info("게시글 JPA 엔티티 삭제 성공: ID={}", boardIdToDelete);
    }
    
    /**
     * 게시글 JPA 엔티티 페이징 조회 기능을 테스트합니다.
     */
    @Test
    @DisplayName("게시글 JPA 엔티티 페이징 조회 테스트")
    void findBoardJpaEntityWithPagingTest() {
        // when
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id").descending());
        Page<BoardJpaEntity> result = boardJpaRepository.findAll(pageable);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.getContent().size()).isEqualTo(10);
        assertThat(result.getTotalElements()).isGreaterThanOrEqualTo(100);
        
        log.info("게시글 JPA 엔티티 페이징 조회 성공: 총 {}개, 현재 페이지 {}개", 
                result.getTotalElements(), result.getContent().size());
    }
    
    /**
     * 게시글 JPA 엔티티에 이미지 추가 기능을 테스트합니다.
     */
    @Test
    @DisplayName("게시글 JPA 엔티티 이미지 추가 테스트")
    void addImageToBoardJpaEntityTest() {
        // given
        BoardJpaEntity entity = boardJpaRepository.findById(testBoardId).orElseThrow();
        String uuid = UUID.randomUUID().toString();
        String filename = "test_image.jpg";
        
        // when
        BoardImageJpaEntity imageEntity = BoardImageJpaEntity.builder()
                .uuid(uuid)
                .filename(filename)
                .ord(1)
                .boardJpaEntity(entity)
                .build();
        
        entity.getImageSet().add(imageEntity);
        boardJpaRepository.save(entity);
        
        // then
        BoardJpaEntity updatedEntity = boardJpaRepository.findById(testBoardId).orElseThrow();
        assertThat(updatedEntity.getImageSet()).isNotEmpty();
        
        log.info("게시글 JPA 엔티티 이미지 추가 성공: 게시글 ID={}, 이미지={}", testBoardId, filename);
    }
    
    /**
     * 게시글 정보 조회 기능을 테스트합니다.
     */
    @Test
    @DisplayName("게시글 정보 조회 테스트")
    public void 테스트_게시글정보보기() {
        Optional<BoardJpaEntity> result = boardJpaRepository.findById(testBoardId);
        BoardJpaEntity boardJpaEntity = result.orElseThrow();

        log.info("게시글 정보 : " + boardJpaEntity);
        log.info("이미지 개수 : " + boardJpaEntity.getImageSet().size());
        
        assertThat(boardJpaEntity).isNotNull();
        assertThat(boardJpaEntity.getTitle()).contains("title");
    }

    /**
     * 게시글 검색 및 댓글 개수 조회 기능을 테스트합니다.
     */
    @Test
    @DisplayName("게시글 검색 및 댓글 개수 조회 테스트")
    public void 테스트_게시글검색_및_댓글개수() {
        String[] types = {"t", "c", "w"};
        String keyword = "1";
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id").descending());

        Page<BoardListReplyCountDTO> result = boardSearchRepository
                .searchWithReplyCount(types, keyword, pageable);

        log.info("Total Pages: " + result.getTotalPages());
        log.info("Page Size: " + result.getSize());
        log.info("Page Number: " + result.getNumber());
        log.info("Has Previous: " + result.hasPrevious() + " Has Next: " + result.hasNext());

        assertThat(result.getContent()).isNotEmpty();
    }

    /**
     * 이미지와 함께 게시글 읽기 기능을 테스트합니다.
     */
    @Test
    @DisplayName("이미지와 함께 게시글 읽기 테스트")
    public void 테스트_이미지와함께_게시글읽기() {
        Optional<BoardJpaEntity> result = boardJpaRepository.findByIdWithImages(testBoardId);
        
        BoardJpaEntity boardJpaEntity = result.orElseThrow(() ->
                new RuntimeException("게시글을 찾을 수 없습니다. ID: " + testBoardId));

        log.info(boardJpaEntity);
        log.info("------------------");

        assertThat(boardJpaEntity).isNotNull();
    }

    /**
     * 검색, 이미지, 댓글 개수 조회 기능을 테스트합니다.
     */
    @Test
    @DisplayName("검색, 이미지, 댓글 개수 조회 테스트")
    public void 테스트_검색_이미지_댓글개수() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id").descending());

        // classId, categoryId, boardType 파라미터 추가 (null = 전체 조회)
        Page<BoardListAllDTO> result = boardSearchRepository.searchWithAll(null, null, null, null, null, pageable);

        log.info("조회된 게시글 수: " + result.getContent().size());
        log.info("전체 게시글 수 : " + result.getTotalElements());

        assertThat(result.getContent()).isNotEmpty();
    }

    /**
     * 클래스별 검색 기능을 테스트합니다.
     */
    @Test
    @DisplayName("클래스별 검색 테스트")
    public void 테스트_클래스별_검색() {
        Pageable pageable = PageRequest.of(0, 5, Sort.by("id").descending());

        // 클래스 1만 조회
        Page<BoardListAllDTO> result1 = boardSearchRepository
                .searchWithAll(null, null, 1L, null, null, pageable);

        // 클래스 2만 조회
        Page<BoardListAllDTO> result2 = boardSearchRepository
                .searchWithAll(null, null, 2L, null, null, pageable);

        log.info("클래스 1 게시글 수: {}", result1.getTotalElements());
        log.info("클래스 2 게시글 수: {}", result2.getTotalElements());

        // 검증
        assertThat(result1.getContent()).isNotEmpty();
        assertThat(result2.getContent()).isNotEmpty();
    }
}