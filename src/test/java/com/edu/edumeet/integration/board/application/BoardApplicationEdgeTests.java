package com.edu.edumeet.board.application;

import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.domain.BoardCategory;
import com.edu.edumeet.board.domain.BoardType;
import com.edu.edumeet.board.infrastructure.BoardCategoryJpaEntity;
import com.edu.edumeet.board.infrastructure.BoardCategoryJpaRepository;
import com.edu.edumeet.board.infrastructure.BoardJpaEntity;
import com.edu.edumeet.board.infrastructure.BoardJpaRepository;
import com.edu.edumeet.board.presentation.BoardService;
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

import static org.assertj.core.api.Assertions.*;

/**
 * 게시판 애플리케이션 레이어의 에지 케이스(경계 조건)를 테스트하는 클래스
 */
@SpringBootTest
@Log4j2
@Transactional
@ActiveProfiles("test")
public class BoardApplicationEdgeTests {

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
     * 제목이 없는 게시글 등록 시 예외가 발생하는지 테스트합니다.
     */
    @Test
    @DisplayName("제목이 없는 게시글 등록 에지 테스트")
    void registerBoardWithEmptyTitle() {
        // given
        BoardDTO boardDTO = BoardDTO.builder()
                .title("")  // 빈 제목
                .content("내용만 있는 게시글")
                .writer("edgeTester")
                .classId(1L)
                .categoryId(testCategoryId)
                .build();
        
        // when & then
        assertThatThrownBy(() -> boardService.register(boardDTO))
                .isInstanceOf(Exception.class);
        
        log.info("제목 없는 게시글 등록 실패 확인");
    }
    
    /**
     * 매우 긴 제목의 게시글 등록 시 예외가 발생하는지 테스트합니다.
     */
    @Test
    @DisplayName("매우 긴 제목의 게시글 등록 에지 테스트")
    void registerBoardWithVeryLongTitle() {
        // given
        String longTitle = "a".repeat(1000);  // 매우 긴 제목
        
        BoardDTO boardDTO = BoardDTO.builder()
                .title(longTitle)
                .content("긴 제목 테스트")
                .writer("edgeTester")
                .classId(1L)
                .categoryId(testCategoryId)
                .build();
        
        // when & then - 예외 발생을 기대해야 함
        assertThatThrownBy(() -> boardService.register(boardDTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("제목이 50자를 초과합니다");
        
        log.info("긴 제목 게시글 등록 실패 확인: 제목 길이={}", longTitle.length());
    }
    
    /**
     * 존재하지 않는 게시글 조회 시 예외가 발생하는지 테스트합니다.
     */
    @Test
    @DisplayName("존재하지 않는 게시글 조회 에지 테스트")
    void readNonExistentBoard() {
        // given
        Long nonExistentId = 99999L;
        
        // when & then
        assertThatThrownBy(() -> boardService.readOne(nonExistentId))
                .isInstanceOf(Exception.class);
        
        log.info("존재하지 않는 게시글 조회 실패 확인: ID={}", nonExistentId);
    }
    
    /**
     * 존재하지 않는 카테고리로 게시글 등록 시 예외가 발생하는지 테스트합니다.
     */
    @Test
    @DisplayName("존재하지 않는 카테고리로 게시글 등록 에지 테스트")
    void registerBoardWithNonExistentCategory() {
        // given
        Long nonExistentCategoryId = 99999L;
        
        BoardDTO boardDTO = BoardDTO.builder()
                .title("잘못된 카테고리 게시글")
                .content("존재하지 않는 카테고리 테스트")
                .writer("edgeTester")
                .classId(1L)
                .categoryId(nonExistentCategoryId)
                .build();
        
        // when & then
        assertThatThrownBy(() -> boardService.register(boardDTO))
                .isInstanceOf(Exception.class);
        
        log.info("존재하지 않는 카테고리로 게시글 등록 실패 확인: 카테고리 ID={}", nonExistentCategoryId);
    }
    
    /**
     * 삭제된 게시글 수정 시 예외가 발생하는지 테스트합니다.
     */
    @Test
    @DisplayName("삭제된 게시글 수정 에지 테스트")
    void modifyDeletedBoard() {
        // given
        boardService.remove(testBoardId);  // 게시글 삭제
        
        // 삭제된 게시글 정보 가져오기 시도
        BoardDTO boardDTO = null;
        try {
            boardDTO = boardService.readOne(testBoardId);
        } catch (Exception e) {
            // 예외가 발생하면 새로운 DTO 생성
            boardDTO = BoardDTO.builder()
                    .id(testBoardId)
                    .title("삭제된 게시글 수정 시도")
                    .content("삭제된 게시글 내용")
                    .writer("edgeTester")
                    .classId(1L)
                    .categoryId(testCategoryId)
                    .build();
        }
        
        final BoardDTO finalBoardDTO = boardDTO;
        
        // when & then
        assertThatThrownBy(() -> boardService.modify(finalBoardDTO))
                .isInstanceOf(Exception.class);
        
        log.info("삭제된 게시글 수정 실패 확인: ID={}", testBoardId);
    }
    
    /**
     * 게시글 타입 변경이 정상적으로 동작하는지 테스트합니다.
     */
    @Test
    @DisplayName("게시글 타입 변경 에지 테스트")
    void changeBoardTypeTest() {
        // given
        BoardDTO boardDTO = boardService.readOne(testBoardId);
        
        // when
        boardDTO.setBoardType(BoardType.NOTICE.name());  // 일반 게시글을 공지사항으로 변경 (String으로 전달)
        boardService.modify(boardDTO);
        
        // then
        BoardDTO updatedBoard = boardService.readOne(testBoardId);
        assertThat(updatedBoard.getBoardType()).isEqualTo(BoardType.NOTICE.name());
        
        log.info("게시글 타입 변경 성공: ID={}, 타입={}", testBoardId, BoardType.NOTICE.name());
    }
    
    /**
     * 페이지 크기 최대값 처리가 정상적으로 동작하는지 테스트합니다.
     */
    @Test
    @DisplayName("페이지 크기 최대값 테스트")
    void maxPageSizeTest() {
        // given
        // 많은 게시글 생성
        for (int i = 0; i < 50; i++) {
            BoardDTO boardDTO = BoardDTO.builder()
                    .title("페이지 테스트 " + i)
                    .content("페이지 테스트 내용 " + i)
                    .writer("pageTester")
                    .classId(1L)
                    .categoryId(testCategoryId)
                    .build();
            boardService.register(boardDTO);
        }
        
        // when
        PageRequestDTO pageRequestDTO = PageRequestDTO.builder()
                .page(1)
                .size(100)  // 매우 큰 페이지 크기
                .build();
        
        PageResponseDTO<BoardDTO> responseDTO = boardService.list(pageRequestDTO);
        
        // then
        assertThat(responseDTO.getDtoList()).isNotEmpty();
        assertThat(responseDTO.getDtoList().size()).isLessThanOrEqualTo(100);
        
        log.info("최대 페이지 크기 테스트 성공: 요청 크기=100, 실제 결과 크기={}", 
                responseDTO.getDtoList().size());
    }
    
    /**
     * 싫어요 최대값 처리가 정상적으로 동작하는지 테스트합니다.
     */
    @Test
    @DisplayName("싫어요 최대값 에지 테스트")
    void dislikeMaxValueTest() {
        // given
        // 싫어요 수를 최대값에 가깝게 설정
        BoardJpaEntity entity = boardJpaRepository.findById(testBoardId).orElseThrow();
        entity.setDislike(Long.MAX_VALUE - 1);
        boardJpaRepository.save(entity);
        
        // when
        long newDislikeCount = boardService.toggleDislike(testBoardId);
        
        // then
        assertThat(newDislikeCount).isEqualTo(Long.MAX_VALUE);
        
        // 실제 DB에 반영되었는지 확인
        BoardDTO updatedBoard = boardService.readOne(testBoardId);
        assertThat(updatedBoard.getDislike()).isEqualTo(Long.MAX_VALUE);
        
        log.info("싫어요 최대값 처리 성공: ID={}, 싫어요 수={}", testBoardId, newDislikeCount);
    }
}