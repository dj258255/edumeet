package com.edu.edumeet.board.presentation;

import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.domain.BoardCategory;
import com.edu.edumeet.board.infrastructure.BoardCategoryJpaEntity;
import com.edu.edumeet.board.infrastructure.BoardCategoryJpaRepository;
import com.edu.edumeet.board.infrastructure.BoardJpaEntity;
import com.edu.edumeet.board.infrastructure.BoardJpaRepository;
import com.edu.edumeet.board.presentation.dto.BoardDTO;
import com.edu.edumeet.board.presentation.dto.PageRequestDTO;
import com.edu.edumeet.board.presentation.dto.PageResponseDTO;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 게시판 프레젠테이션 레이어의 정상적인 사용 시나리오를 테스트하는 클래스
 */
@SpringBootTest
@Log4j2
@Transactional
@ActiveProfiles("test")
public class BoardPresentationExecutionTests {

    @Autowired
    private BoardService boardService;

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
     * 게시글 등록 기능을 테스트합니다.
     */
    @Test
    @DisplayName("게시글 등록 실행 테스트")
    void registerBoardTest() {
        // given
        BoardDTO boardDTO = BoardDTO.builder()
                .title("새 게시글")
                .content("새 게시글 내용")
                .writer("newUser")
                .classId(1L)
                .categoryId(testCategoryId)
                .build();
        
        // when
        Long newBoardId = boardService.register(boardDTO);
        
        // then
        BoardDTO savedBoard = boardService.readOne(newBoardId);
        assertThat(savedBoard).isNotNull();
        assertThat(savedBoard.getTitle()).isEqualTo("새 게시글");
        assertThat(savedBoard.getContent()).isEqualTo("새 게시글 내용");
        assertThat(savedBoard.getWriter()).isEqualTo("newUser");
        
        log.info("게시글 등록 성공: ID={}", newBoardId);
    }
    
    /**
     * 게시글 수정 기능을 테스트합니다.
     */
    @Test
    @DisplayName("게시글 수정 실행 테스트")
    void modifyBoardTest() {
        // given
        BoardDTO boardDTO = boardService.readOne(testBoardId);
        boardDTO.setTitle("수정된 제목");
        boardDTO.setContent("수정된 내용");
        
        // when
        boardService.modify(boardDTO);
        
        // then
        BoardDTO modifiedBoard = boardService.readOne(testBoardId);
        assertThat(modifiedBoard.getTitle()).isEqualTo("수정된 제목");
        assertThat(modifiedBoard.getContent()).isEqualTo("수정된 내용");
        
        log.info("게시글 수정 성공: ID={}", testBoardId);
    }
    
    /**
     * 게시글 목록 조회 기능을 테스트합니다.
     */
    @Test
    @DisplayName("게시글 목록 조회 실행 테스트")
    void listBoardTest() {
        // given
        // 추가 게시글 생성
        for (int i = 0; i < 5; i++) {
            BoardDTO boardDTO = BoardDTO.builder()
                    .title("목록 테스트 " + i)
                    .content("목록 테스트 내용 " + i)
                    .writer("listTester")
                    .classId(1L)
                    .categoryId(testCategoryId)
                    .build();
            boardService.register(boardDTO);
        }
        
        // when
        PageRequestDTO pageRequestDTO = PageRequestDTO.builder()
                .page(1)
                .size(10)
                .build();
        
        PageResponseDTO<BoardDTO> responseDTO = boardService.list(pageRequestDTO);
        
        // then
        assertThat(responseDTO.getDtoList()).isNotEmpty();
        assertThat(responseDTO.getDtoList().size()).isGreaterThanOrEqualTo(6); // 기존 1개 + 추가 5개
        
        log.info("게시글 목록 조회 성공: 총 {}개", responseDTO.getDtoList().size());
    }
    
    /**
     * 카테고리별 게시글 조회 기능을 테스트합니다.
     */
    @Test
    @DisplayName("카테고리별 게시글 조회 실행 테스트")
    void listByCategoryTest() {
        // given
        // 다른 카테고리 생성
        BoardCategory otherCategory = BoardCategory.builder()
                .categoryName("다른 카테고리")
                .classId(1L)
                .createdBy("categoryTester")
                .build();
        
        BoardCategoryJpaEntity savedOtherCategory = boardCategoryJpaRepository.save(BoardCategoryJpaEntity.fromDomain(otherCategory));
        Long otherCategoryId = savedOtherCategory.getId();
        
        // 다른 카테고리에 게시글 추가
        BoardDTO otherCategoryBoard = BoardDTO.builder()
                .title("다른 카테고리 게시글")
                .content("다른 카테고리 내용")
                .writer("categoryTester")
                .classId(1L)
                .categoryId(otherCategoryId)
                .build();
        
        boardService.register(otherCategoryBoard);
        
        // when - 카테고리 필터링은 PageRequestDTO에 조건을 추가하여 수행
        PageRequestDTO pageRequestDTO = PageRequestDTO.builder()
                .page(1)
                .size(10)
                .categoryId(testCategoryId) // 카테고리 ID를 검색 조건으로 추가
                .build();
        
        PageResponseDTO<BoardDTO> responseDTO = boardService.list(pageRequestDTO);
        
        // then
        assertThat(responseDTO.getDtoList()).isNotEmpty();
        assertThat(responseDTO.getDtoList()).allMatch(board -> board.getCategoryId().equals(testCategoryId));
        
        log.info("카테고리별 게시글 조회 성공: 카테고리 ID={}, 게시글 수={}", 
                testCategoryId, responseDTO.getDtoList().size());
    }
    
    /**
     * 게시글 이미지 추가 기능을 테스트합니다.
     */
    @Test
    @DisplayName("게시글 이미지 추가 실행 테스트")
    void addImageTest() {
        // given
        BoardDTO boardDTO = boardService.readOne(testBoardId);
        String uuid = UUID.randomUUID().toString();
        String fileName = "test_image.jpg";
        String fullFileName = uuid + "_" + fileName;
        
        // when - BoardService의 addImageToBoard 메서드 사용
        boardService.addImageToBoard(testBoardId, uuid, fileName);
        
        // then
        BoardDTO updatedBoard = boardService.readOne(testBoardId);
        assertThat(updatedBoard.getFileNames()).isNotEmpty();
        assertThat(updatedBoard.getFileNames()).contains(fullFileName);
        
        log.info("게시글 이미지 추가 성공: 게시글 ID={}, 이미지={}", testBoardId, fileName);
    }
}