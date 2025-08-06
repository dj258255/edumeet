package com.edu.edumeet.board.presentation;

import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.domain.BoardCategory;
import com.edu.edumeet.board.domain.BoardType;
import com.edu.edumeet.board.infrastructure.BoardCategoryJpaEntity;
import com.edu.edumeet.board.infrastructure.BoardCategoryJpaRepository;
import com.edu.edumeet.board.infrastructure.BoardJpaEntity;
import com.edu.edumeet.board.infrastructure.BoardJpaRepository;
import com.edu.edumeet.board.presentation.dto.BoardDTO;
import com.edu.edumeet.board.presentation.dto.PageRequestDTO;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 게시판 프레젠테이션 계층에 대한 에지 테스트
 * 경계 조건이나 예외 상황을 테스트
 */
@SpringBootTest
@Log4j2
@Transactional
@ActiveProfiles("test")
public class BoardPresentationEdgeTests {

    @Autowired
    private BoardService boardService;

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
                .build();
        
        BoardJpaEntity savedBoard = boardJpaRepository.save(BoardJpaEntity.fromDomain(board));
        testBoardId = savedBoard.getId();
        
        log.info("테스트 준비 완료: 카테고리 ID={}, 게시글 ID={}", testCategoryId, testBoardId);
    }
    
    @Test
    @DisplayName("제목이 없는 게시글 등록 에지 테스트")
    void registerBoardWithoutTitleTest() {
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
    
    @Test
    @DisplayName("매우 긴 제목의 게시글 등록 에지 테스트")
    void registerBoardWithLongTitleTest() {
        // given
        String longTitle = "a".repeat(1000);  // 매우 긴 제목
        
        BoardDTO boardDTO = BoardDTO.builder()
                .title(longTitle)
                .content("긴 제목 테스트")
                .writer("edgeTester")
                .classId(1L)
                .categoryId(testCategoryId)
                .build();
        
        // when & then
        assertThatThrownBy(() -> boardService.register(boardDTO))
                .isInstanceOf(Exception.class);
        
        log.info("긴 제목 게시글 등록 실패 확인: 제목 길이={}", longTitle.length());
    }
    
    @Test
    @DisplayName("존재하지 않는 게시글 조회 에지 테스트")
    void readNonExistentBoardTest() {
        // given
        Long nonExistentId = 99999L;
        
        // when & then
        assertThatThrownBy(() -> boardService.readOne(nonExistentId))
                .isInstanceOf(Exception.class);
        
        log.info("존재하지 않는 게시글 조회 실패 확인: ID={}", nonExistentId);
    }
    
    @Test
    @DisplayName("존재하지 않는 카테고리로 게시글 등록 에지 테스트")
    void registerBoardWithNonExistentCategoryTest() {
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
    
    @Test
    @DisplayName("삭제된 게시글 수정 에지 테스트")
    void modifyDeletedBoardTest() {
        // given
        boardService.remove(testBoardId);  // 게시글 삭제
        
        BoardDTO deletedBoard = BoardDTO.builder()
                .id(testBoardId)
                .title("삭제된 게시글 수정 시도")
                .content("삭제된 게시글 내용")
                .build();
        
        // when & then
        assertThatThrownBy(() -> boardService.modify(deletedBoard))
                .isInstanceOf(Exception.class);
        
        log.info("삭제된 게시글 수정 실패 확인: ID={}", testBoardId);
    }
    
    @Test
    @DisplayName("게시글 타입 변경 에지 테스트")
    void changeBoardTypeTest() {
        // given
        BoardDTO boardDTO = boardService.readOne(testBoardId);
        
        // when
        boardDTO.setBoardType(BoardType.NOTICE.name());  // 일반 게시글을 공지사항으로 변경
        
        // then
        assertThatThrownBy(() -> boardService.modify(boardDTO))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("공지사항");
        
        log.info("권한 없는 사용자의 공지사항 변경 실패 확인: ID={}", testBoardId);
    }
    
    @Test
    @DisplayName("페이지 크기 최대값 테스트")
    void maxPageSizeTest() {
        // given
        int veryLargePageSize = 1000;  // 매우 큰 페이지 크기
        
        // when
        PageRequestDTO pageRequestDTO = PageRequestDTO.builder()
                .page(1)
                .size(veryLargePageSize)
                .build();
        
        // then
        // 시스템이 매우 큰 페이지 크기를 처리할 수 있는지 확인
        // 실패하지 않고 결과를 반환해야 함
        assertThat(boardService.list(pageRequestDTO)).isNotNull();
        
        log.info("매우 큰 페이지 크기 처리 성공: 요청 크기={}", veryLargePageSize);
    }
    
    @Test
    @DisplayName("잘못된 페이지 번호 테스트")
    void invalidPageNumberTest() {
        // given
        int invalidPageNumber = -1;  // 음수 페이지 번호
        
        // when
        PageRequestDTO pageRequestDTO = PageRequestDTO.builder()
                .page(invalidPageNumber)
                .size(10)
                .build();
        
        // then
        // 시스템이 잘못된 페이지 번호를 적절히 처리하는지 확인
        assertThat(boardService.list(pageRequestDTO)).isNotNull();
        
        log.info("잘못된 페이지 번호 처리 성공: 페이지 번호={}", invalidPageNumber);
    }
}