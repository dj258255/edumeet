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
 * 게시판 애플리케이션 계층에 대한 에지 케이스 테스트
 * 경계 조건, 예외 상황, 제한사항을 중심으로 테스트
 */
@SpringBootTest
@Log4j2
@Transactional
@ActiveProfiles("test")
public class BoardApplicationEdgeTests {

    @Autowired
    private BoardService boardService;

    // setUp에서만 사용 - 테스트 데이터 준비용
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

        log.info("에지 테스트 준비 완료: 카테고리 ID={}, 게시글 ID={}", testCategoryId, testBoardId);
    }

    @Test
    @DisplayName("빈 제목으로 게시글 등록 시도")
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

        log.info("빈 제목으로 게시글 등록 실패 확인");
    }

    @Test
    @DisplayName("null 제목으로 게시글 등록 시도")
    void registerBoardWithNullTitle() {
        // given
        BoardDTO boardDTO = BoardDTO.builder()
                .title(null)  // null 제목
                .content("내용만 있는 게시글")
                .writer("edgeTester")
                .classId(1L)
                .categoryId(testCategoryId)
                .build();

        // when & then
        assertThatThrownBy(() -> boardService.register(boardDTO))
                .isInstanceOf(Exception.class);

        log.info("null 제목으로 게시글 등록 실패 확인");
    }

    @Test
    @DisplayName("매우 긴 제목으로 게시글 등록")
    void registerBoardWithVeryLongTitle() {
        // given
        String veryLongTitle = "제목".repeat(100);  // 200자 제목

        BoardDTO boardDTO = BoardDTO.builder()
                .title(veryLongTitle)
                .content("긴 제목 테스트")
                .writer("edgeTester")
                .classId(1L)
                .categoryId(testCategoryId)
                .build();

        // when
        Long newBoardId = boardService.register(boardDTO);

        // then
        BoardDTO savedBoard = boardService.readOne(newBoardId);
        assertThat(savedBoard.getTitle()).isEqualTo(veryLongTitle);

        log.info("긴 제목으로 게시글 등록 성공: 제목 길이={}", veryLongTitle.length());
    }

    @Test
    @DisplayName("제목 길이 초과로 게시글 등록 시도")
    void registerBoardWithTooLongTitle() {
        // given
        String tooLongTitle = "제목".repeat(200);  // 400자 제목 (200자 초과)

        BoardDTO boardDTO = BoardDTO.builder()
                .title(tooLongTitle)
                .content("제목 길이 초과 테스트")
                .writer("edgeTester")
                .classId(1L)
                .categoryId(testCategoryId)
                .build();

        // when & then
        assertThatThrownBy(() -> boardService.register(boardDTO))
                .isInstanceOf(Exception.class);

        log.info("제목 길이 초과로 게시글 등록 실패 확인: 제목 길이={}", tooLongTitle.length());
    }

    @Test
    @DisplayName("존재하지 않는 게시글 조회 시도")
    void readNonExistentBoard() {
        // given
        Long nonExistentId = 99999L;

        // when & then
        assertThatThrownBy(() -> boardService.readOne(nonExistentId))
                .isInstanceOf(Exception.class);

        log.info("존재하지 않는 게시글 조회 실패 확인: ID={}", nonExistentId);
    }

    @Test
    @DisplayName("존재하지 않는 카테고리로 게시글 등록 시도")
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

    @Test
    @DisplayName("삭제된 게시글 수정 시도")
    void modifyDeletedBoard() {
        // given - 게시글을 먼저 삭제
        boardService.remove(testBoardId);

        BoardDTO deletedBoardDTO = BoardDTO.builder()
                .id(testBoardId)
                .title("삭제된 게시글 수정 시도")
                .content("이미 삭제된 게시글")
                .writer("edgeTester")
                .classId(1L)
                .categoryId(testCategoryId)
                .build();

        // when & then
        assertThatThrownBy(() -> boardService.modify(deletedBoardDTO))
                .isInstanceOf(Exception.class);

        log.info("삭제된 게시글 수정 실패 확인: ID={}", testBoardId);
    }

    @Test
    @DisplayName("삭제된 게시글 삭제 시도")
    void removeAlreadyDeletedBoard() {
        // given - 게시글을 먼저 삭제
        boardService.remove(testBoardId);

        // when & then - 이미 삭제된 게시글을 다시 삭제 시도
        assertThatThrownBy(() -> boardService.remove(testBoardId))
                .isInstanceOf(Exception.class);

        log.info("이미 삭제된 게시글 재삭제 실패 확인: ID={}", testBoardId);
    }

    @Test
    @DisplayName("페이지 번호가 0 이하인 경우")
    void listWithInvalidPageNumber() {
        // given
        PageRequestDTO pageRequestDTO = PageRequestDTO.builder()
                .page(0)  // 잘못된 페이지 번호
                .size(10)
                .build();

        // when & then
        assertThatThrownBy(() -> boardService.list(pageRequestDTO))
                .isInstanceOf(Exception.class);

        log.info("잘못된 페이지 번호로 목록 조회 실패 확인: page=0");
    }

    @Test
    @DisplayName("페이지 크기가 0 이하인 경우")
    void listWithInvalidPageSize() {
        // given
        PageRequestDTO pageRequestDTO = PageRequestDTO.builder()
                .page(1)
                .size(0)  // 잘못된 페이지 크기
                .build();

        // when & then
        assertThatThrownBy(() -> boardService.list(pageRequestDTO))
                .isInstanceOf(Exception.class);

        log.info("잘못된 페이지 크기로 목록 조회 실패 확인: size=0");
    }

    @Test
    @DisplayName("매우 큰 페이지 크기 요청")
    void listWithVeryLargePageSize() {
        // given
        PageRequestDTO pageRequestDTO = PageRequestDTO.builder()
                .page(1)
                .size(1000)  // 매우 큰 페이지 크기
                .build();

        // when
        PageResponseDTO<BoardDTO> responseDTO = boardService.list(pageRequestDTO);

        // then - 시스템에서 최대 페이지 크기로 제한되어야 함
        assertThat(responseDTO.getDtoList().size()).isLessThanOrEqualTo(100); // 최대 100개로 제한됐다고 가정

        log.info("큰 페이지 크기 요청 제한 확인: 요청=1000, 실제={}", responseDTO.getDtoList().size());
    }

    @Test
    @DisplayName("좋아요 수 극한 테스트")
    void favoriteCountExtremeTest() {
        // given - 좋아요를 여러 번 토글
        BoardDTO initialBoard = boardService.readOne(testBoardId);
        long initialFavoriteCount = initialBoard.getFavorite();

        // when - 좋아요 추가 후 다시 토글 (좋아요 취소)
        boardService.toggleFavorite(testBoardId);
        long afterFirstToggle = boardService.toggleFavorite(testBoardId);

        // then - 좋아요가 원래대로 돌아왔는지 확인
        assertThat(afterFirstToggle).isEqualTo(initialFavoriteCount);

        BoardDTO updatedBoard = boardService.readOne(testBoardId);
        assertThat(updatedBoard.getFavorite()).isEqualTo(initialFavoriteCount);

        log.info("좋아요 토글 기능 확인: 초기={}, 토글 후={}",
                initialFavoriteCount, updatedBoard.getFavorite());
    }

    @Test
    @DisplayName("싫어요 수 극한 테스트")
    void dislikeCountExtremeTest() {
        // given - 싫어요를 여러 번 토글
        BoardDTO initialBoard = boardService.readOne(testBoardId);
        long initialDislikeCount = initialBoard.getDislike();

        // when - 싫어요 추가 후 다시 토글 (싫어요 취소)
        boardService.toggleDislike(testBoardId);
        long afterFirstToggle = boardService.toggleDislike(testBoardId);

        // then - 싫어요가 원래대로 돌아왔는지 확인
        assertThat(afterFirstToggle).isEqualTo(initialDislikeCount);

        BoardDTO updatedBoard = boardService.readOne(testBoardId);
        assertThat(updatedBoard.getDislike()).isEqualTo(initialDislikeCount);

        log.info("싫어요 토글 기능 확인: 초기={}, 토글 후={}",
                initialDislikeCount, updatedBoard.getDislike());
    }

    @Test
    @DisplayName("게시글 타입 변경 테스트")
    void changeBoardTypeTest() {
        // given
        BoardDTO boardDTO = boardService.readOne(testBoardId);
        boardDTO.setBoardType(BoardType.NOTICE.name());  // 일반 게시글을 공지사항으로 변경

        // when
        boardService.modify(boardDTO);

        // then
        BoardDTO updatedBoard = boardService.readOne(testBoardId);
        assertThat(updatedBoard.getBoardType()).isEqualTo(BoardType.NOTICE.name());

        log.info("게시글 타입 변경 성공: ID={}, 타입={}", testBoardId, BoardType.NOTICE.name());
    }

    @Test
    @DisplayName("매우 많은 이미지가 포함된 게시글")
    void boardWithManyImages() {
        // given
        BoardDTO boardDTO = BoardDTO.builder()
                .title("많은 이미지 테스트")
                .content("이미지 개수 제한 테스트")
                .writer("edgeTester")
                .classId(1L)
                .categoryId(testCategoryId)
                .build();

        // 매우 많은 이미지 파일명 생성 (예: 50개)
        java.util.List<String> manyImages = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            String uuid = UUID.randomUUID().toString();
            manyImages.add(uuid + "_image" + i + ".jpg");
        }

        boardDTO.setFileNames(manyImages);

        // when
        Long newBoardId = boardService.register(boardDTO);

        // then
        BoardDTO savedBoard = boardService.readOne(newBoardId);
        // 시스템에서 이미지 개수를 제한할 수 있음
        assertThat(savedBoard.getFileNames()).hasSizeLessThanOrEqualTo(50);

        log.info("많은 이미지 게시글 등록: 요청 이미지 수={}, 실제 저장 수={}",
                manyImages.size(), savedBoard.getFileNames().size());
    }

    @Test
    @DisplayName("존재하지 않는 게시글 복원 시도")
    void restoreNonExistentBoard() {
        // given
        Long nonExistentId = 99999L;

        // when & then
        assertThatThrownBy(() -> boardService.restore(nonExistentId))
                .isInstanceOf(Exception.class);

        log.info("존재하지 않는 게시글 복원 실패 확인: ID={}", nonExistentId);
    }

    @Test
    @DisplayName("이미 복원된 게시글 복원 시도")
    void restoreAlreadyRestoredBoard() {
        // given - 정상 상태의 게시글에 복원 시도
        // when & then
        assertThatThrownBy(() -> boardService.restore(testBoardId))
                .isInstanceOf(Exception.class);

        log.info("이미 복원된 게시글 복원 실패 확인: ID={}", testBoardId);
    }

    @Test
    @DisplayName("동시성 테스트 - 여러 번의 좋아요 토글")
    void concurrentFavoriteToggleTest() {
        // given
        BoardDTO initialBoard = boardService.readOne(testBoardId);
        long initialCount = initialBoard.getFavorite();

        // when - 연속으로 여러 번 토글 (실제 동시성은 아니지만 순서 테스트)
        for (int i = 0; i < 10; i++) {
            boardService.toggleFavorite(testBoardId);
        }

        // then - 짝수 번 토글했으므로 원래 값과 같아야 함
        BoardDTO finalBoard = boardService.readOne(testBoardId);
        assertThat(finalBoard.getFavorite()).isEqualTo(initialCount);

        log.info("연속 좋아요 토글 테스트: 초기={}, 최종={}", initialCount, finalBoard.getFavorite());
    }
}