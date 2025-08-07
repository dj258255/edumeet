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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Log4j2
public class BoardConcurrencyTests {
/*
    @Autowired
    private BoardService boardService;

    @Autowired
    private BoardJpaRepository boardJpaRepository;

    @Autowired
    private BoardCategoryJpaRepository boardCategoryJpaRepository;

    private Long classId;
    private Long boardId;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        // 테스트 데이터 초기화
        boardJpaRepository.deleteAll();
        boardCategoryJpaRepository.deleteAll();

        // 클래스 ID 설정
        classId = 1L;

        // 카테고리 생성
        BoardCategoryJpaEntity categoryEntity = BoardCategoryJpaEntity.builder()
                .categoryName("Test Category")
                .classId(classId)
                .createdBy("tester")
                .isActive(true)
                .sortOrder(1)
                .build();
        categoryId = boardCategoryJpaRepository.save(categoryEntity).getId();

        // 게시글 생성
        BoardDTO boardDTO = BoardDTO.builder()
                .title("Test Board")
                .content("Test Content")
                .writer("tester")
                .classId(classId)
                .categoryId(categoryId)
                .boardType(BoardType.NORMAL.name())
                .build();

        boardId = boardService.register(boardDTO);
        log.info("Test board created with ID: {}", boardId);
    }

    @Test
    @DisplayName("동시에 여러 사용자가 게시글을 조회하는 경우 조회수가 정확히 증가하는지 테스트")
    public void testConcurrentViews() throws Exception {
        // Given
        int numberOfThreads = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        // When
        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(() -> {
                try {
                    // 게시글 조회 (조회수 증가)
                    boardService.readOne(boardId);
                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드가 완료될 때까지 대기
        latch.await();
        executorService.shutdown();

        // Then
        BoardJpaEntity boardEntity = boardJpaRepository.findById(boardId).orElseThrow();
        log.info("Final view count: {}", boardEntity.getView());
        
        // 초기 조회수 0 + 테스트에서 10번 조회
        assertThat(boardEntity.getView()).isEqualTo(numberOfThreads);
    }

    @Test
    @DisplayName("동시에 여러 사용자가 게시글에 좋아요를 누르는 경우 좋아요 수가 정확히 증가하는지 테스트")
    public void testConcurrentFavorites() throws Exception {
        // Given
        int numberOfThreads = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        // When
        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(() -> {
                try {
                    // 좋아요 토글 (증가)
                    boardService.toggleFavorite(boardId);
                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드가 완료될 때까지 대기
        latch.await();
        executorService.shutdown();

        // Then
        BoardJpaEntity boardEntity = boardJpaRepository.findById(boardId).orElseThrow();
        log.info("Final favorite count: {}", boardEntity.getFavorite());
        
        // 초기 좋아요 수 0 + 테스트에서 10번 좋아요
        assertThat(boardEntity.getFavorite()).isEqualTo(numberOfThreads);
    }

    @Test
    @DisplayName("동시에 여러 사용자가 게시글에 싫어요를 누르는 경우 싫어요 수가 정확히 증가하는지 테스트")
    public void testConcurrentDislikes() throws Exception {
        // Given
        int numberOfThreads = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        // When
        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(() -> {
                try {
                    // 싫어요 토글 (증가)
                    boardService.toggleDislike(boardId);
                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드가 완료될 때까지 대기
        latch.await();
        executorService.shutdown();

        // Then
        BoardJpaEntity boardEntity = boardJpaRepository.findById(boardId).orElseThrow();
        log.info("Final dislike count: {}", boardEntity.getDislike());
        
        // 초기 싫어요 수 0 + 테스트에서 10번 싫어요
        assertThat(boardEntity.getDislike()).isEqualTo(numberOfThreads);
    }

    @Test
    @DisplayName("동시에 여러 사용자가 게시글을 조회하고 좋아요와 싫어요를 누르는 복합 테스트")
    public void testConcurrentMixedOperations() throws Exception {
        // Given
        int numberOfThreads = 30; // 10개씩 조회, 좋아요, 싫어요
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        List<Future<?>> futures = new ArrayList<>();

        // When
        // 10개의 조회 요청
        for (int i = 0; i < 10; i++) {
            futures.add(executorService.submit(() -> {
                try {
                    boardService.readOne(boardId);
                } finally {
                    latch.countDown();
                }
            }));
        }

        // 10개의 좋아요 요청
        for (int i = 0; i < 10; i++) {
            futures.add(executorService.submit(() -> {
                try {
                    boardService.toggleFavorite(boardId);
                } finally {
                    latch.countDown();
                }
            }));
        }

        // 10개의 싫어요 요청
        for (int i = 0; i < 10; i++) {
            futures.add(executorService.submit(() -> {
                try {
                    boardService.toggleDislike(boardId);
                } finally {
                    latch.countDown();
                }
            }));
        }

        // 모든 스레드가 완료될 때까지 대기
        latch.await();
        executorService.shutdown();

        // Then
        BoardJpaEntity boardEntity = boardJpaRepository.findById(boardId).orElseThrow();
        log.info("Final view count: {}", boardEntity.getView());
        log.info("Final favorite count: {}", boardEntity.getFavorite());
        log.info("Final dislike count: {}", boardEntity.getDislike());
        
        assertThat(boardEntity.getView()).isEqualTo(10);
        assertThat(boardEntity.getFavorite()).isEqualTo(10);
        assertThat(boardEntity.getDislike()).isEqualTo(10);
    }
    */
}