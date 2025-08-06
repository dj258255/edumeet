package com.edu.edumeet.board;

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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 게시판 기능에 대한 실행 테스트와 에지 테스트를 포함하는 테스트 클래스
 * 실행 테스트: 정상적인 사용 시나리오를 테스트
 * 에지 테스트: 경계 조건이나 예외 상황을 테스트
 */
@SpringBootTest
@Log4j2
@Transactional
@ActiveProfiles("test")
public class BoardExecutionAndEdgeTests {

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

    /**
     * 실행 테스트: 정상적인 사용 시나리오를 테스트하는 클래스
     */
    @Nested
    @DisplayName("게시판 실행 테스트")
    class ExecutionTests {
        
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
        
        @Test
        @DisplayName("게시글 삭제 실행 테스트")
        void removeBoardTest() {
            // when
            boardService.remove(testBoardId);
            
            // then
            // 삭제된 게시글은 조회되지 않거나 다른 방식으로 확인해야 함
            // 여기서는 예외가 발생하는지 확인
            assertThatThrownBy(() -> boardService.readOne(testBoardId))
                    .isInstanceOf(Exception.class);
            
            log.info("게시글 삭제 성공: ID={}", testBoardId);
        }
        
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
        
        @Test
        @DisplayName("게시글 싫어요 추가 실행 테스트")
        void toggleDislikeTest() {
            // given
            BoardDTO boardDTO = boardService.readOne(testBoardId);
            long initialDislikeCount = boardDTO.getDislike();
            
            // when
            long newDislikeCount = boardService.toggleDislike(testBoardId);
            
            // then
            assertThat(newDislikeCount).isEqualTo(initialDislikeCount + 1);
            
            // 실제 DB에 반영되었는지 확인
            BoardDTO updatedBoard = boardService.readOne(testBoardId);
            assertThat(updatedBoard.getDislike()).isEqualTo(initialDislikeCount + 1);
            
            log.info("게시글 싫어요 추가 성공: ID={}, 싫어요 수={}", testBoardId, newDislikeCount);
        }
    }
    
    /**
     * 에지 테스트: 경계 조건이나 예외 상황을 테스트하는 클래스
     */
    @Nested
    @DisplayName("게시판 에지 테스트")
    class EdgeTests {
        
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
            
            // when
            Long newBoardId = boardService.register(boardDTO);
            
            // then
            BoardDTO savedBoard = boardService.readOne(newBoardId);
            assertThat(savedBoard.getTitle()).isEqualTo(longTitle);
            
            log.info("긴 제목 게시글 등록 성공: 제목 길이={}", longTitle.length());
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
            
            BoardDTO deletedBoard = boardService.readOne(testBoardId);
            deletedBoard.setTitle("삭제된 게시글 수정 시도");
            
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
            boardDTO.setBoardType(BoardType.NOTICE.name());  // 일반 게시글을 공지사항으로 변경 (String으로 전달)
            boardService.modify(boardDTO);
            
            // then
            BoardDTO updatedBoard = boardService.readOne(testBoardId);
            assertThat(updatedBoard.getBoardType()).isEqualTo(BoardType.NOTICE.name());
            
            log.info("게시글 타입 변경 성공: ID={}, 타입={}", testBoardId, BoardType.NOTICE.name());
        }
        
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
}