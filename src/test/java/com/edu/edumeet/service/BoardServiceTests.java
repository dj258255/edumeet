package com.edu.edumeet.service;

import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.infrastructure.BoardJpaEntity;
import com.edu.edumeet.board.infrastructure.BoardJpaRepository;
import com.edu.edumeet.board.presentation.BoardService;
import com.edu.edumeet.board.presentation.dto.*;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Log4j2
@Transactional
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BoardServiceTests {

    @Autowired
    private BoardService boardService;

    @Autowired
    private BoardJpaRepository boardJpaRepository;

    private Long testBoardId;
    private Long testBoardWithImagesId;

    @BeforeAll
    public void 테스트_데이터_생성() {
        boardJpaRepository.deleteAll();
        log.info("테스트 데이터 생성 시작");

        // 기본 게시글 생성
        Board board1 = Board.builder()
                .title("Test Board 1")
                .content("Test Content 1")
                .writer("testUser1")
                .classId(1L)
                .build();

        BoardJpaEntity savedBoard1 = boardJpaRepository.save(BoardJpaEntity.fromDomain(board1));
        testBoardId = savedBoard1.getId();

        // 이미지가 있는 게시글 생성
        Board board2 = Board.builder()
                .title("테스트 보드 with 이미지")
                .content("테스트 내용 with 이미지")
                .writer("테스트_유저2")
                .classId(1L)
                .build();

        // 이미지 추가
        for (int i = 0; i < 3; i++) {
            board2.addImage(UUID.randomUUID().toString(), "testfile" + i + ".jpg");
        }

        BoardJpaEntity savedBoard2 = boardJpaRepository.save(BoardJpaEntity.fromDomain(board2));
        testBoardWithImagesId = savedBoard2.getId();

        log.info("테스트 데이터 생성 완료. 기본 게시글 ID: {}, 이미지 게시글 ID: {}",
                testBoardId, testBoardWithImagesId);
    }

    @Test
    public void 테스트_게시글_등록() {
        log.info(boardService.getClass().getName());

        BoardDTO boardDTO = BoardDTO.builder()
                .title("Sample Title...")
                .content("Sample Content...")
                .writer("user00")
                .classId(1L)
                .build();

        Long boardId = boardService.register(boardDTO);
        log.info("등록된 게시글 ID: " + boardId);
        
        // 등록된 게시글 확인
        BoardDTO savedBoard = boardService.readOne(boardId);
        assertThat(savedBoard).isNotNull();
        assertThat(savedBoard.getTitle()).isEqualTo("Sample Title...");
    }

    @Test
    public void 테스트_게시글_수정() {
        // 게시글 수정
        BoardDTO boardDTO = BoardDTO.builder()
                .id(testBoardId)
                .title("수정된 제목")
                .content("수정된 내용")
                .build();

        boardService.modify(boardDTO);
        
        // 수정된 게시글 확인
        BoardDTO modifiedBoard = boardService.readOne(testBoardId);
        assertThat(modifiedBoard.getTitle()).isEqualTo("수정된 제목");
        assertThat(modifiedBoard.getContent()).isEqualTo("수정된 내용");
        log.info("게시글 수정 완료. ID: {}, 제목: {}", modifiedBoard.getId(), modifiedBoard.getTitle());
    }

    @Test
    public void 테스트_게시글_목록_조회() {
        PageRequestDTO pageRequestDTO = PageRequestDTO.builder()
                .page(1)
                .size(10)
                .build();

        PageResponseDTO<BoardDTO> responseDTO = boardService.list(pageRequestDTO);
        
        assertThat(responseDTO.getDtoList()).isNotEmpty();
        log.info("총 게시글 수: {}, 현재 페이지: {}", responseDTO.getTotal(), responseDTO.getPage());
    }

    @Test
    public void 테스트_이미지_처리() {
        // 이미지가 있는 게시글 조회
        BoardDTO boardWithImages = boardService.readOne(testBoardWithImagesId);
        assertThat(boardWithImages).isNotNull();
        assertThat(boardWithImages.getFileNames()).isNotEmpty();
        
        log.info("이미지가 있는 게시글 조회: ID={}, 제목={}, 이미지 수={}", 
                boardWithImages.getId(), boardWithImages.getTitle(), boardWithImages.getFileNames().size());
        
        // 이미지가 있는 새 게시글 등록
        BoardDTO newBoardDTO = BoardDTO.builder()
                .title("이미지 테스트 게시글")
                .content("이미지 테스트 내용")
                .writer("tester")
                .classId(1L)
                .build();

        newBoardDTO.setFileNames(Arrays.asList(
                UUID.randomUUID() + "_test1.jpg",
                UUID.randomUUID() + "_test2.jpg"
        ));
        
        Long newBoardId = boardService.register(newBoardDTO);
        
        // 등록된 게시글 확인
        BoardDTO savedBoard = boardService.readOne(newBoardId);
        assertThat(savedBoard.getFileNames()).hasSize(2);
        
        // 이미지 수정 테스트
        BoardDTO updateDTO = BoardDTO.builder()
                .id(newBoardId)
                .title("이미지 수정 테스트")
                .content("이미지 수정 내용")
                .build();
                
        updateDTO.setFileNames(Arrays.asList(UUID.randomUUID() + "_updated.jpg"));
        boardService.modify(updateDTO);
        
        // 수정된 게시글 확인
        BoardDTO updatedBoard = boardService.readOne(newBoardId);
        assertThat(updatedBoard.getFileNames()).hasSize(1);
    }

    @Test
    public void 테스트_게시글_삭제() {
        // 삭제용 게시글 생성
        BoardDTO boardDTO = BoardDTO.builder()
                .title("삭제 테스트")
                .content("삭제될 게시글")
                .writer("tester")
                .classId(1L)
                .build();
                
        Long boardToDeleteId = boardService.register(boardDTO);
        
        // 삭제 전 확인
        BoardDTO beforeDelete = boardService.readOne(boardToDeleteId);
        assertThat(beforeDelete).isNotNull();
        
        // 게시글 삭제
        boardService.remove(boardToDeleteId);
        
        // 삭제 확인 - 예외 발생 여부 확인
        try {
            boardService.readOne(boardToDeleteId);
            // 여기까지 오면 삭제가 안된 것
            assertThat(false).isTrue(); // 강제 실패
        } catch (Exception e) {
            // 예외 발생 시 정상 (삭제됨)
            log.info("게시글이 정상적으로 삭제됨: {}", boardToDeleteId);
        }
    }

    @Test
    public void 테스트_게시글_상세조회() {
        PageRequestDTO pageRequestDTO = PageRequestDTO.builder()
                .page(1)
                .size(10)
                .build();

        PageResponseDTO<BoardListAllDTO> responseDTO = boardService.listWithAll(pageRequestDTO);
        
        assertThat(responseDTO.getDtoList()).isNotEmpty();
        
        List<BoardListAllDTO> dtoList = responseDTO.getDtoList();
        dtoList.forEach(dto -> {
            log.info("ID: {}, 제목: {}, 조회수: {}, 좋아요: {}, 이미지 수: {}", 
                    dto.getId(), dto.getTitle(), dto.getView(), dto.getFavorite(),
                    dto.getBoardImages() != null ? dto.getBoardImages().size() : 0);
            
            assertThat(dto.getView()).isNotNull();
            assertThat(dto.getFavorite()).isNotNull();
        });
    }

    @Test
    public void 테스트_조회수_기능() {
        // 게시글 조회 전 조회수 확인
        BoardDTO beforeRead = boardService.readOne(testBoardId);
        long initialViewCount = beforeRead.getView();
        log.info("초기 조회수: {}", initialViewCount);
        
        // 게시글 여러 번 조회 (3번)
        for (int i = 0; i < 3; i++) {
            boardService.readOne(testBoardId);
        }
        
        // 마지막 조회 후 조회수 확인
        BoardDTO afterMultipleReads = boardService.readOne(testBoardId);
        long finalViewCount = afterMultipleReads.getView();
        log.info("여러 번 조회 후 조회수: {}", finalViewCount);
        
        // 조회수가 증가했는지 확인 (3번 + 마지막 확인 1번 = 4번 증가)
        assertThat(finalViewCount).isEqualTo(initialViewCount + 4);
        
        // 게시글 수정 후에도 조회수가 유지되는지 확인
        BoardDTO updateDTO = BoardDTO.builder()
                .id(testBoardId)
                .title("조회수 테스트 제목 수정")
                .content("조회수 테스트 내용 수정")
                .build();
        boardService.modify(updateDTO);
        
        // 수정 후 조회
        BoardDTO afterUpdate = boardService.readOne(testBoardId);
        assertThat(afterUpdate.getView()).isEqualTo(finalViewCount + 1); // 조회로 인해 1 증가
        assertThat(afterUpdate.getTitle()).isEqualTo("조회수 테스트 제목 수정");
    }

    @Test
    public void 테스트_좋아요_기능() {
        // 초기 좋아요 수 확인
        BoardDTO initialBoard = boardService.readOne(testBoardId);
        long initialFavoriteCount = initialBoard.getFavorite();
        log.info("초기 좋아요 수: {}", initialFavoriteCount);
        
        // 좋아요 토글 (추가)
        long favoriteCountAfterToggle = boardService.toggleFavorite(testBoardId);
        log.info("토글 후 좋아요 수: {}", favoriteCountAfterToggle);
        
        // 좋아요가 1 증가했는지 확인
        assertThat(favoriteCountAfterToggle).isEqualTo(initialFavoriteCount + 1);
        
        // 다시 좋아요 토글 (취소)
        long favoriteCountAfterSecondToggle = boardService.toggleFavorite(testBoardId);
        log.info("두 번째 토글 후 좋아요 수: {}", favoriteCountAfterSecondToggle);
        
        // 좋아요가 다시 초기값으로 돌아갔는지 확인
        assertThat(favoriteCountAfterSecondToggle).isEqualTo(initialFavoriteCount);
        
        // 여러 게시글의 좋아요가 독립적으로 동작하는지 확인
        BoardDTO secondBoardBefore = boardService.readOne(testBoardWithImagesId);
        long secondBoardInitialFavorite = secondBoardBefore.getFavorite();
        
        // 두 번째 게시글만 좋아요 토글
        boardService.toggleFavorite(testBoardWithImagesId);
        
        // 두 게시글 모두 다시 조회
        BoardDTO firstBoardAfter = boardService.readOne(testBoardId);
        BoardDTO secondBoardAfter = boardService.readOne(testBoardWithImagesId);
        
        // 첫 번째 게시글은 변화 없음, 두 번째 게시글은 좋아요 1 증가
        assertThat(firstBoardAfter.getFavorite()).isEqualTo(initialFavoriteCount);
        assertThat(secondBoardAfter.getFavorite()).isEqualTo(secondBoardInitialFavorite + 1);
    }
}