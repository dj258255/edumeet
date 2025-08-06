package com.edu.edumeet.board.application;

import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.domain.BoardCategory;
import com.edu.edumeet.board.infrastructure.BoardCategoryJpaEntity;
import com.edu.edumeet.board.infrastructure.BoardCategoryJpaRepository;
import com.edu.edumeet.board.infrastructure.BoardJpaEntity;
import com.edu.edumeet.board.infrastructure.BoardJpaRepository;
import com.edu.edumeet.board.presentation.BoardService;
import com.edu.edumeet.board.presentation.dto.BoardDTO;
import com.edu.edumeet.board.presentation.dto.BoardListReplyCountDTO;
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

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 게시판 애플리케이션 계층에 대한 실행 테스트
 * 정상적인 사용 시나리오를 중심으로 테스트
 */
@SpringBootTest
@Log4j2
@Transactional
@ActiveProfiles("test")
public class BoardApplicationExecutionTests {

    @Autowired
    private BoardService boardService;

    // setUp에서만 사용 - 테스트 데이터 준비용
    @Autowired
    private BoardJpaRepository boardJpaRepository;

    @Autowired
    private BoardCategoryJpaRepository boardCategoryJpaRepository;

    private Long testCategoryId;
    private Long testBoardId;
    private Long testBoardWithImagesId;

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

        // 이미지가 있는 테스트용 게시글 생성
        Board boardWithImages = Board.builder()
                .title("이미지 테스트 게시글")
                .content("이미지 테스트 내용")
                .writer("tester")
                .classId(1L)
                .categoryId(testCategoryId)
                .build();

        // 이미지 추가
        for (int i = 0; i < 3; i++) {
            boardWithImages.addImage(UUID.randomUUID().toString(), "testfile" + i + ".jpg");
        }

        BoardJpaEntity savedBoardWithImages = boardJpaRepository.save(BoardJpaEntity.fromDomain(boardWithImages));
        testBoardWithImagesId = savedBoardWithImages.getId();

        log.info("테스트 준비 완료: 카테고리 ID={}, 게시글 ID={}, 이미지 게시글 ID={}",
                testCategoryId, testBoardId, testBoardWithImagesId);
    }

    @Test
    @DisplayName("게시글 등록 - 정상 시나리오")
    void registerBoardNormalFlow() {
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
    @DisplayName("게시글 수정 - 정상 시나리오")
    void modifyBoardNormalFlow() {
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
    @DisplayName("게시글 삭제 - 정상 시나리오")
    void removeBoardNormalFlow() {
        // given
        Long boardIdToDelete = testBoardId;

        // when
        boardService.remove(boardIdToDelete);

        // then
        // 삭제된 게시글은 목록에서 조회되지 않아야 함
        PageRequestDTO pageRequestDTO = PageRequestDTO.builder()
                .page(1)
                .size(10)
                .build();

        PageResponseDTO<BoardDTO> responseDTO = boardService.list(pageRequestDTO);
        boolean isDeleted = responseDTO.getDtoList().stream()
                .noneMatch(board -> board.getId().equals(boardIdToDelete));

        assertThat(isDeleted).isTrue();

        log.info("게시글 삭제 성공: ID={}", boardIdToDelete);
    }

    @Test
    @DisplayName("게시글 목록 조회 - 페이징 처리")
    void listBoardWithPagination() {
        // given
        // 추가 게시글 생성
        for (int i = 0; i < 15; i++) {
            BoardDTO boardDTO = BoardDTO.builder()
                    .title("목록 테스트 " + i)
                    .content("목록 테스트 내용 " + i)
                    .writer("listTester")
                    .classId(1L)
                    .categoryId(testCategoryId)
                    .build();
            boardService.register(boardDTO);
        }

        // when - 첫 번째 페이지
        PageRequestDTO pageRequestDTO1 = PageRequestDTO.builder()
                .page(1)
                .size(10)
                .build();

        PageResponseDTO<BoardDTO> responseDTO1 = boardService.list(pageRequestDTO1);

        // when - 두 번째 페이지
        PageRequestDTO pageRequestDTO2 = PageRequestDTO.builder()
                .page(2)
                .size(10)
                .build();

        PageResponseDTO<BoardDTO> responseDTO2 = boardService.list(pageRequestDTO2);

        // then
        assertThat(responseDTO1.getDtoList()).hasSize(10);
        assertThat(responseDTO2.getDtoList()).isNotEmpty();
        assertThat(responseDTO1.getTotal()).isGreaterThanOrEqualTo(17); // 기존 2개 + 추가 15개

        log.info("페이징 조회 성공: 1페이지={}개, 2페이지={}개, 총={}개",
                responseDTO1.getDtoList().size(),
                responseDTO2.getDtoList().size(),
                responseDTO1.getTotal());
    }

    @Test
    @DisplayName("댓글 수가 포함된 게시글 목록 조회")
    void listWithReplyCountNormalFlow() {
        // when
        PageRequestDTO pageRequestDTO = PageRequestDTO.builder()
                .page(1)
                .size(10)
                .build();

        PageResponseDTO<BoardListReplyCountDTO> responseDTO = boardService.listWithReplyCount(pageRequestDTO);

        // then
        assertThat(responseDTO.getDtoList()).isNotEmpty();
        responseDTO.getDtoList().forEach(board -> {
            assertThat(board.getId()).isNotNull();
            assertThat(board.getTitle()).isNotNull();
            assertThat(board.getReplyCount()).isNotNull();
            assertThat(board.getReplyCount()).isGreaterThanOrEqualTo(0L);
        });

        log.info("댓글 수가 포함된 게시글 목록 조회 성공: 총 {}개", responseDTO.getDtoList().size());
    }

    @Test
    @DisplayName("이미지가 포함된 게시글 처리")
    void imageProcessingNormalFlow() {
        // 기존 이미지 게시글 조회 테스트
        BoardDTO boardWithImages = boardService.readOne(testBoardWithImagesId);
        assertThat(boardWithImages).isNotNull();
        assertThat(boardWithImages.getFileNames()).isNotEmpty();
        assertThat(boardWithImages.getFileNames()).hasSize(3);

        log.info("이미지가 있는 게시글 조회: ID={}, 제목={}, 이미지 수={}",
                boardWithImages.getId(), boardWithImages.getTitle(), boardWithImages.getFileNames().size());

        // 새로운 이미지 게시글 등록 테스트
        BoardDTO newBoardDTO = BoardDTO.builder()
                .title("이미지 테스트 게시글")
                .content("이미지 테스트 내용")
                .writer("tester")
                .classId(1L)
                .categoryId(testCategoryId)
                .build();

        String uuid1 = UUID.randomUUID().toString();
        String uuid2 = UUID.randomUUID().toString();

        newBoardDTO.setFileNames(Arrays.asList(
                uuid1 + "_test1.jpg",
                uuid2 + "_test2.jpg"
        ));

        Long newBoardId = boardService.register(newBoardDTO);

        // then
        BoardDTO savedBoard = boardService.readOne(newBoardId);
        assertThat(savedBoard.getFileNames()).hasSize(2);
        assertThat(savedBoard.getFileNames()).contains(uuid1 + "_test1.jpg");
        assertThat(savedBoard.getFileNames()).contains(uuid2 + "_test2.jpg");

        log.info("이미지가 있는 게시글 등록 성공: ID={}, 이미지 수={}", newBoardId, savedBoard.getFileNames().size());
    }

    @Test
    @DisplayName("게시글 조회수 증가")
    void viewCountIncreaseNormalFlow() {
        // given
        BoardDTO initialBoard = boardService.readOne(testBoardId);
        long initialViewCount = initialBoard.getView();

        // when - 여러 번 조회
        for (int i = 0; i < 5; i++) {
            boardService.readOne(testBoardId);
        }

        // then
        BoardDTO updatedBoard = boardService.readOne(testBoardId);
        assertThat(updatedBoard.getView()).isGreaterThan(initialViewCount);

        log.info("조회수 증가 확인: 초기 조회수={}, 현재 조회수={}",
                initialViewCount, updatedBoard.getView());
    }

    @Test
    @DisplayName("게시글 좋아요/싫어요 기능")
    void favoriteDislikeNormalFlow() {
        // given
        BoardDTO initialBoard = boardService.readOne(testBoardId);
        long initialFavoriteCount = initialBoard.getFavorite();
        long initialDislikeCount = initialBoard.getDislike();

        log.info("🔍 초기값 - 좋아요: {}, 싫어요: {}", initialFavoriteCount, initialDislikeCount);

        // when - 좋아요 추가
        long newFavoriteCount = boardService.toggleFavorite(testBoardId);
        log.info("🔍 toggleFavorite 반환값: {}", newFavoriteCount);

        // then - 좋아요 확인
        assertThat(newFavoriteCount).isEqualTo(initialFavoriteCount + 1);

        BoardDTO favoriteBoard = boardService.readOne(testBoardId);
        log.info("🔍 DB에서 다시 조회한 좋아요 수: {}", favoriteBoard.getFavorite());
        assertThat(favoriteBoard.getFavorite()).isEqualTo(initialFavoriteCount + 1);

        // when - 싫어요 추가
        long newDislikeCount = boardService.toggleDislike(testBoardId);
        log.info("🔍 toggleDislike 반환값: {}", newDislikeCount);

        // then - 싫어요 확인
        assertThat(newDislikeCount).isEqualTo(initialDislikeCount + 1);

        BoardDTO dislikedBoard = boardService.readOne(testBoardId);
        log.info("🔍 DB에서 다시 조회한 싫어요 수: {}", dislikedBoard.getDislike());
        assertThat(dislikedBoard.getDislike()).isEqualTo(initialDislikeCount + 1);

        log.info("좋아요/싫어요 기능 확인: 좋아요={}, 싫어요={}",
                favoriteBoard.getFavorite(), dislikedBoard.getDislike());
    }

    @Test
    @DisplayName("카테고리별 게시글 조회")
    void listBoardsByCategoryNormalFlow() {
        // given - 다른 카테고리 생성 (setUp에서만 JpaRepository 사용)
        BoardCategory otherCategory = BoardCategory.builder()
                .categoryName("다른 카테고리")
                .classId(1L)
                .createdBy("tester")
                .build();

        BoardCategoryJpaEntity savedOtherCategory = boardCategoryJpaRepository.save(BoardCategoryJpaEntity.fromDomain(otherCategory));
        Long otherCategoryId = savedOtherCategory.getId();

        // 다른 카테고리에 게시글 추가 - BoardService 사용
        BoardDTO otherCategoryBoard = BoardDTO.builder()
                .title("다른 카테고리 게시글")
                .content("다른 카테고리 내용")
                .writer("categoryTester")
                .classId(1L)
                .categoryId(otherCategoryId)
                .build();

        boardService.register(otherCategoryBoard);

        // when - 첫 번째 카테고리로 필터링
        PageRequestDTO pageRequestDTO = PageRequestDTO.builder()
                .page(1)
                .size(10)
                .categoryId(testCategoryId)
                .build();

        PageResponseDTO<BoardDTO> responseDTO = boardService.list(pageRequestDTO);

        // then
        assertThat(responseDTO.getDtoList()).isNotEmpty();
        assertThat(responseDTO.getDtoList()).allMatch(board -> board.getCategoryId().equals(testCategoryId));

        log.info("카테고리별 게시글 조회 성공: 카테고리 ID={}, 게시글 수={}",
                testCategoryId, responseDTO.getDtoList().size());
    }

    @Test
    @DisplayName("게시글 복원 기능")
    void restoreBoardNormalFlow() {
        // given - 게시글 삭제
        boardService.remove(testBoardId);
        
        // 삭제 직후 상태 확인
        log.info("삭제 후 - 단건 조회 시도");
        try {
            BoardDTO deletedBoard = boardService.readOne(testBoardId);
            log.info("삭제 후에도 조회됨: {}", deletedBoard.getTitle());
        } catch (Exception e) {
            log.info("삭제 후 단건 조회 실패: {}", e.getMessage());
        }

        // when - 게시글 복원
        log.info("복원 시작");
        boardService.restore(testBoardId);
        log.info("복원 완료");
        
        // 복원 직후 상태 확인
        log.info("복원 후 - 단건 조회 시도");
        try {
            BoardDTO restoredBoard = boardService.readOne(testBoardId);
            log.info("복원 후 조회 성공: {}", restoredBoard.getTitle());
            assertThat(restoredBoard).isNotNull();
            assertThat(restoredBoard.getTitle()).isEqualTo("테스트 게시글");
        } catch (Exception e) {
            log.error("복원 후에도 조회 실패: {}", e.getMessage());
            throw e;
        }
        
        // then - 목록에서도 확인
        PageRequestDTO pageRequestDTO = PageRequestDTO.builder()
                .page(1)
                .size(10)
                .build();
        
        PageResponseDTO<BoardDTO> listAfterRestore = boardService.list(pageRequestDTO);
        boolean isRestored = listAfterRestore.getDtoList().stream()
                .anyMatch(board -> board.getId().equals(testBoardId));
        
        log.info("목록에서 복원된 게시글 존재 여부: {}", isRestored);
        log.info("목록 총 개수: {}, 목록 내용: {}", 
                listAfterRestore.getDtoList().size(),
                listAfterRestore.getDtoList().stream().map(b -> b.getId() + ":" + b.getTitle()).collect(Collectors.toList()));
        
        assertThat(isRestored).isTrue();
    }
}