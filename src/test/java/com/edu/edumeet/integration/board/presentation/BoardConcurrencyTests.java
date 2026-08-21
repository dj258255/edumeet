package com.edu.edumeet.integration.board.presentation;

import com.edu.edumeet.board.domain.BoardType;
import com.edu.edumeet.board.domain.BoardCategoryJpaEntity;
import com.edu.edumeet.board.repository.BoardCategoryJpaRepository;
import com.edu.edumeet.board.domain.BoardJpaEntity;
import com.edu.edumeet.board.repository.BoardJpaRepository;
import com.edu.edumeet.board.service.BoardService;
import com.edu.edumeet.board.dto.BoardDTO;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.LockModeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Log4j2
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BoardConcurrencyTests {
//    @Autowired
//    private BoardService boardService;
//
//    @Autowired
//    private BoardJpaRepository boardJpaRepository;
//
//    @Autowired
//    private BoardCategoryJpaRepository boardCategoryJpaRepository;
//
//    @PersistenceContext
//    private EntityManager entityManager;
//
//    private Long classId;
//    private Long boardId;
//    private Long categoryId;
//    private static final int MAX_THREADS = 100;
//    private static final int MEDIUM_THREADS = 50;
//    private static final int SMALL_THREADS = 10;
//
//    @BeforeEach
//    void setUp() {
//        // 테스트 데이터 초기화
//        boardJpaRepository.deleteAll();
//        boardCategoryJpaRepository.deleteAll();
//
//        // 클래스 ID 설정
//        classId = 1L;
//
//        // 카테고리 생성
//        BoardCategoryJpaEntity categoryEntity = BoardCategoryJpaEntity.builder()
//                .categoryName("Test Category")
//                .classId(classId)
//                .createdBy("tester")
//                .isActive(true)
//                .sortOrder(1)
//                .build();
//        categoryId = boardCategoryJpaRepository.save(categoryEntity).getId();
//
//        // 게시글 생성
//        BoardDTO boardDTO = BoardDTO.builder()
//                .title("Test Board")
//                .content("Test Content")
//                .writer("tester")
//                .classId(classId)
//                .categoryId(categoryId)
//                .boardType(BoardType.NORMAL.name())
//                .build();
//
//        boardId = boardService.register(boardDTO);
//        log.info("Test board created with ID: {}", boardId);
//    }
//
//    @Test
//    @DisplayName("동시에 여러 사용자가 게시글을 조회하는 경우 조회수가 정확히 증가하는지 테스트")
//    public void testConcurrentViews() throws Exception {
//        // Given
//        int numberOfThreads = SMALL_THREADS;
//        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
//        CountDownLatch latch = new CountDownLatch(numberOfThreads);
//
//        // When
//        for (int i = 0; i < numberOfThreads; i++) {
//            executorService.submit(() -> {
//                try {
//                    // 게시글 조회 (조회수 증가)
//                    boardService.readOne(boardId);
//                } finally {
//                    latch.countDown();
//                }
//            });
//        }
//
//        // 모든 스레드가 완료될 때까지 대기
//        latch.await();
//        executorService.shutdown();
//
//        // Then
//        BoardJpaEntity boardEntity = boardJpaRepository.findById(boardId).orElseThrow();
//        log.info("Final view count: {}", boardEntity.getView());
//
//        // 초기 조회수 0 + 테스트에서 10번 조회
//        assertThat(boardEntity.getView()).isEqualTo(numberOfThreads);
//    }
//
//    @Test
//    @DisplayName("동시에 여러 사용자가 게시글에 좋아요를 누르는 경우 좋아요 수가 정확히 증가하는지 테스트")
//    public void testConcurrentFavorites() throws Exception {
//        // Given
//        int numberOfThreads = SMALL_THREADS;
//        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
//        CountDownLatch latch = new CountDownLatch(numberOfThreads);
//
//        // When
//        for (int i = 0; i < numberOfThreads; i++) {
//            executorService.submit(() -> {
//                try {
//                    // 좋아요 토글 (증가)
//                    boardService.toggleFavorite(boardId);
//                } finally {
//                    latch.countDown();
//                }
//            });
//        }
//
//        // 모든 스레드가 완료될 때까지 대기
//        latch.await();
//        executorService.shutdown();
//
//        // Then
//        BoardJpaEntity boardEntity = boardJpaRepository.findById(boardId).orElseThrow();
//        log.info("Final favorite count: {}", boardEntity.getFavorite());
//
//        // 초기 좋아요 수 0 + 테스트에서 10번 좋아요
//        assertThat(boardEntity.getFavorite()).isEqualTo(numberOfThreads);
//    }
//
//    @Test
//    @DisplayName("동시에 여러 사용자가 게시글에 싫어요를 누르는 경우 싫어요 수가 정확히 증가하는지 테스트")
//    public void testConcurrentDislikes() throws Exception {
//        // Given
//        int numberOfThreads = SMALL_THREADS;
//        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
//        CountDownLatch latch = new CountDownLatch(numberOfThreads);
//
//        // When
//        for (int i = 0; i < numberOfThreads; i++) {
//            executorService.submit(() -> {
//                try {
//                    // 싫어요 토글 (증가)
//                    boardService.toggleDislike(boardId);
//                } finally {
//                    latch.countDown();
//                }
//            });
//        }
//
//        // 모든 스레드가 완료될 때까지 대기
//        latch.await();
//        executorService.shutdown();
//
//        // Then
//        BoardJpaEntity boardEntity = boardJpaRepository.findById(boardId).orElseThrow();
//        log.info("Final dislike count: {}", boardEntity.getDislike());
//
//        // 초기 싫어요 수 0 + 테스트에서 10번 싫어요
//        assertThat(boardEntity.getDislike()).isEqualTo(numberOfThreads);
//    }
//
//    @Test
//    @DisplayName("동시에 여러 사용자가 게시글을 조회하고 좋아요와 싫어요를 누르는 복합 테스트")
//    public void testConcurrentMixedOperations() throws Exception {
//        // Given
//        int numberOfThreads = 30; // 10개씩 조회, 좋아요, 싫어요
//        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
//        CountDownLatch latch = new CountDownLatch(numberOfThreads);
//        List<Future<?>> futures = new ArrayList<>();
//
//        // When
//        // 10개의 조회 요청
//        for (int i = 0; i < 10; i++) {
//            futures.add(executorService.submit(() -> {
//                try {
//                    boardService.readOne(boardId);
//                } finally {
//                    latch.countDown();
//                }
//            }));
//        }
//
//        // 10개의 좋아요 요청
//        for (int i = 0; i < 10; i++) {
//            futures.add(executorService.submit(() -> {
//                try {
//                    boardService.toggleFavorite(boardId);
//                } finally {
//                    latch.countDown();
//                }
//            }));
//        }
//
//        // 10개의 싫어요 요청
//        for (int i = 0; i < 10; i++) {
//            futures.add(executorService.submit(() -> {
//                try {
//                    boardService.toggleDislike(boardId);
//                } finally {
//                    latch.countDown();
//                }
//            }));
//        }
//
//        // 모든 스레드가 완료될 때까지 대기
//        latch.await();
//        executorService.shutdown();
//
//        // Then
//        BoardJpaEntity boardEntity = boardJpaRepository.findById(boardId).orElseThrow();
//        log.info("Final view count: {}", boardEntity.getView());
//        log.info("Final favorite count: {}", boardEntity.getFavorite());
//        log.info("Final dislike count: {}", boardEntity.getDislike());
//
//        assertThat(boardEntity.getView()).isEqualTo(10);
//        assertThat(boardEntity.getFavorite()).isEqualTo(10);
//        assertThat(boardEntity.getDislike()).isEqualTo(10);
//    }
//
//    @Test
//    @DisplayName("대용량 스레드 환경에서의 조회수 증가 테스트 (100개 스레드)")
//    public void testHighConcurrencyViews() throws Exception {
//        // Given
//        int numberOfThreads = MAX_THREADS;
//        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
//        CountDownLatch latch = new CountDownLatch(numberOfThreads);
//        AtomicInteger errorCount = new AtomicInteger(0);
//        long startTime = System.currentTimeMillis();
//
//        // When
//        for (int i = 0; i < numberOfThreads; i++) {
//            executorService.submit(() -> {
//                try {
//                    boardService.readOne(boardId);
//                } catch (Exception e) {
//                    errorCount.incrementAndGet();
//                    log.error("Error in high concurrency test: ", e);
//                } finally {
//                    latch.countDown();
//                }
//            });
//        }
//
//        latch.await();
//        executorService.shutdown();
//        long endTime = System.currentTimeMillis();
//
//        // Then
//        BoardJpaEntity boardEntity = boardJpaRepository.findById(boardId).orElseThrow();
//        log.info("High concurrency test - Final view count: {}, Error count: {}, Duration: {}ms",
//                boardEntity.getView(), errorCount.get(), (endTime - startTime));
//
//        // 오류 발생 없이 모든 조회가 성공했는지 확인
//        assertThat(errorCount.get()).isEqualTo(0);
//        assertThat(boardEntity.getView()).isEqualTo(numberOfThreads);
//    }
//
//    @DisplayName("반복 실행을 통한 동시성 안정성 테스트")
//    @RepeatedTest(5)
//    public void testRepeatedConcurrency() throws Exception {
//        // Given
//        int numberOfThreads = 20;
//        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
//        CountDownLatch latch = new CountDownLatch(numberOfThreads);
//        AtomicInteger successCount = new AtomicInteger(0);
//
//        // 각 반복마다 새로운 게시글 생성
//        BoardDTO newBoardDTO = BoardDTO.builder()
//                .title("Repeated Test Board " + System.currentTimeMillis())
//                .content("Test Content")
//                .writer("tester")
//                .classId(classId)
//                .categoryId(categoryId)
//                .boardType(BoardType.NORMAL.name())
//                .build();
//
//        Long testBoardId = boardService.register(newBoardDTO);
//
//        // When
//        for (int i = 0; i < numberOfThreads; i++) {
//            executorService.submit(() -> {
//                try {
//                    boardService.readOne(testBoardId);
//                    successCount.incrementAndGet();
//                } finally {
//                    latch.countDown();
//                }
//            });
//        }
//
//        latch.await();
//        executorService.shutdown();
//
//        // Then
//        BoardJpaEntity boardEntity = boardJpaRepository.findById(testBoardId).orElseThrow();
//        log.info("Repeated test - View count: {}, Success count: {}",
//                boardEntity.getView(), successCount.get());
//
//        assertThat(boardEntity.getView()).isEqualTo(numberOfThreads);
//        assertThat(successCount.get()).isEqualTo(numberOfThreads);
//    }
//
//    @Test
//    @DisplayName("READ_COMMITTED 격리 수준에서의 동시성 테스트")
//    @Transactional(isolation = Isolation.READ_COMMITTED)
//    public void testReadCommittedIsolation() throws Exception {
//        // Given
//        int numberOfThreads = MEDIUM_THREADS;
//        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
//        CountDownLatch latch = new CountDownLatch(numberOfThreads);
//        List<Long> viewCounts = Collections.synchronizedList(new ArrayList<>());
//
//        // When
//        for (int i = 0; i < numberOfThreads; i++) {
//            executorService.submit(() -> {
//                try {
//                    boardService.readOne(boardId);
//                    // 조회 후 현재 조회수를 기록
//                    BoardJpaEntity entity = boardJpaRepository.findById(boardId).orElseThrow();
//                    viewCounts.add(entity.getView());
//                } finally {
//                    latch.countDown();
//                }
//            });
//        }
//
//        latch.await();
//        executorService.shutdown();
//
//        // Then
//        BoardJpaEntity boardEntity = boardJpaRepository.findById(boardId).orElseThrow();
//        log.info("READ_COMMITTED test - Final view count: {}", boardEntity.getView());
//        log.info("Observed view counts during execution: {}", viewCounts);
//
//        assertThat(boardEntity.getView()).isEqualTo(numberOfThreads);
//    }
//
//    @Test
//    @DisplayName("REPEATABLE_READ 격리 수준에서의 동시성 테스트")
//    @Transactional(isolation = Isolation.REPEATABLE_READ)
//    public void testRepeatableReadIsolation() throws Exception {
//        // Given
//        int numberOfThreads = MEDIUM_THREADS;
//        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
//        CountDownLatch latch = new CountDownLatch(numberOfThreads);
//
//        // When
//        for (int i = 0; i < numberOfThreads; i++) {
//            executorService.submit(() -> {
//                try {
//                    boardService.readOne(boardId);
//                } finally {
//                    latch.countDown();
//                }
//            });
//        }
//
//        latch.await();
//        executorService.shutdown();
//
//        // Then
//        BoardJpaEntity boardEntity = boardJpaRepository.findById(boardId).orElseThrow();
//        log.info("REPEATABLE_READ test - Final view count: {}", boardEntity.getView());
//
//        assertThat(boardEntity.getView()).isEqualTo(numberOfThreads);
//    }
//
//    @Test
//    @DisplayName("낙관적 락 상황에서의 동시성 테스트")
//    public void testOptimisticLockingConcurrency() throws Exception {
//        // Given
//        int numberOfThreads = 20;
//        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
//        CountDownLatch latch = new CountDownLatch(numberOfThreads);
//        AtomicInteger optimisticLockExceptions = new AtomicInteger(0);
//        AtomicInteger successCount = new AtomicInteger(0);
//
//        // When
//        for (int i = 0; i < numberOfThreads; i++) {
//            executorService.submit(() -> {
//                try {
//                    // 낙관적 락이 적용될 수 있는 업데이트 작업 시뮬레이션
//                    BoardDTO updateDTO = BoardDTO.builder()
//                            .id(boardId)
//                            .title("Updated Title " + System.currentTimeMillis())
//                            .content("Updated Content")
//                            .writer("tester")
//                            .classId(classId)
//                            .categoryId(categoryId)
//                            .boardType(BoardType.NORMAL.name())
//                            .build();
//
//                    boardService.modify(updateDTO);
//                    successCount.incrementAndGet();
//                } catch (OptimisticLockingFailureException e) {
//                    optimisticLockExceptions.incrementAndGet();
//                    log.warn("Optimistic locking exception occurred");
//                } catch (Exception e) {
//                    log.error("Unexpected exception: ", e);
//                } finally {
//                    latch.countDown();
//                }
//            });
//        }
//
//        latch.await();
//        executorService.shutdown();
//
//        // Then
//        log.info("Optimistic lock test - Success: {}, Lock exceptions: {}",
//                successCount.get(), optimisticLockExceptions.get());
//
//        // 최소한 하나는 성공해야 하고, 나머지는 낙관적 락 예외가 발생할 수 있음
//        assertThat(successCount.get() + optimisticLockExceptions.get()).isEqualTo(numberOfThreads);
//        assertThat(successCount.get()).isGreaterThan(0);
//    }
//
//    @Test
//    @DisplayName("비관적 락을 사용한 동시성 제어 테스트")
//    @Transactional
//    public void testPessimisticLockingConcurrency() throws Exception {
//        // Given
//        int numberOfThreads = 10;
//        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
//        CountDownLatch latch = new CountDownLatch(numberOfThreads);
//        AtomicLong totalExecutionTime = new AtomicLong(0);
//        List<Long> executionTimes = Collections.synchronizedList(new ArrayList<>());
//
//        // When
//        for (int i = 0; i < numberOfThreads; i++) {
//            final int threadIndex = i;
//            executorService.submit(() -> {
//                long startTime = System.currentTimeMillis();
//                try {
//                    // 비관적 락으로 엔티티 조회 및 업데이트
//                    BoardJpaEntity entity = entityManager.find(BoardJpaEntity.class, boardId, LockModeType.PESSIMISTIC_WRITE);
//
//                    // 작업 시뮬레이션 (짧은 대기)
//                    Thread.sleep(10);
//
//                    // 조회수 증가
//                    entity.incrementView();
//                    entityManager.flush();
//
//                } catch (Exception e) {
//                    log.error("Error in pessimistic lock test: ", e);
//                } finally {
//                    long endTime = System.currentTimeMillis();
//                    long executionTime = endTime - startTime;
//                    executionTimes.add(executionTime);
//                    totalExecutionTime.addAndGet(executionTime);
//                    latch.countDown();
//                }
//            });
//        }
//
//        latch.await();
//        executorService.shutdown();
//
//        // Then
//        BoardJpaEntity boardEntity = boardJpaRepository.findById(boardId).orElseThrow();
//        log.info("Pessimistic lock test - Final view count: {}", boardEntity.getView());
//        log.info("Average execution time per thread: {}ms", totalExecutionTime.get() / numberOfThreads);
//        log.info("Execution times: {}", executionTimes);
//
//        assertThat(boardEntity.getView()).isEqualTo(numberOfThreads);
//    }
//
//    @Test
//    @DisplayName("스트레스 테스트: 혼합된 읽기/쓰기 작업")
//    public void testStressMixedOperations() throws Exception {
//        // Given
//        int totalOperations = 200;
//        int readThreads = 120;  // 60% 읽기
//        int writeThreads = 80;  // 40% 쓰기
//
//        ExecutorService executorService = Executors.newFixedThreadPool(50);
//        CountDownLatch latch = new CountDownLatch(totalOperations);
//
//        AtomicInteger readSuccess = new AtomicInteger(0);
//        AtomicInteger writeSuccess = new AtomicInteger(0);
//        AtomicInteger errors = new AtomicInteger(0);
//
//        long startTime = System.currentTimeMillis();
//
//        // 읽기 작업들
//        for (int i = 0; i < readThreads; i++) {
//            executorService.submit(() -> {
//                try {
//                    boardService.readOne(boardId);
//                    readSuccess.incrementAndGet();
//                } catch (Exception e) {
//                    errors.incrementAndGet();
//                    log.error("Read operation error: ", e);
//                } finally {
//                    latch.countDown();
//                }
//            });
//        }
//
//        // 쓰기 작업들 (좋아요/싫어요)
//        for (int i = 0; i < writeThreads; i++) {
//            final int operation = i % 2; // 0: 좋아요, 1: 싫어요
//            executorService.submit(() -> {
//                try {
//                    if (operation == 0) {
//                        boardService.toggleFavorite(boardId);
//                    } else {
//                        boardService.toggleDislike(boardId);
//                    }
//                    writeSuccess.incrementAndGet();
//                } catch (Exception e) {
//                    errors.incrementAndGet();
//                    log.error("Write operation error: ", e);
//                } finally {
//                    latch.countDown();
//                }
//            });
//        }
//
//        // When
//        latch.await(30, TimeUnit.SECONDS);
//        executorService.shutdown();
//        long endTime = System.currentTimeMillis();
//
//        // Then
//        BoardJpaEntity boardEntity = boardJpaRepository.findById(boardId).orElseThrow();
//
//        log.info("Stress test completed in {}ms", (endTime - startTime));
//        log.info("Read success: {}, Write success: {}, Errors: {}",
//                readSuccess.get(), writeSuccess.get(), errors.get());
//        log.info("Final state - Views: {}, Favorites: {}, Dislikes: {}",
//                boardEntity.getView(), boardEntity.getFavorite(), boardEntity.getDislike());
//
//        // 기본적인 무결성 검증
//        assertThat(readSuccess.get() + writeSuccess.get() + errors.get()).isEqualTo(totalOperations);
//        // 오류율 5% 미만 (정수 비교로 변경)
//        assertThat(errors.get()).isLessThanOrEqualTo((int)(totalOperations * 0.05));
//    }
//
//    @Test
//    @DisplayName("데드락 상황 시뮬레이션 테스트")
//    public void testDeadlockSimulation() throws Exception {
//        // Given
//        // 두 개의 추가 게시글 생성
//        BoardDTO board2DTO = BoardDTO.builder()
//                .title("Test Board 2")
//                .content("Test Content 2")
//                .writer("tester")
//                .classId(classId)
//                .categoryId(categoryId)
//                .boardType(BoardType.NORMAL.name())
//                .build();
//        Long board2Id = boardService.register(board2DTO);
//
//        int numberOfThreads = 10;
//        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
//        CountDownLatch latch = new CountDownLatch(numberOfThreads);
//        AtomicInteger deadlockExceptions = new AtomicInteger(0);
//        AtomicInteger successCount = new AtomicInteger(0);
//
//        // When - 두 게시글에 대해 서로 다른 순서로 접근하여 데드락 유발 시도
//        for (int i = 0; i < numberOfThreads; i++) {
//            final boolean reverseOrder = (i % 2 == 0);
//            executorService.submit(() -> {
//                try {
//                    if (reverseOrder) {
//                        // 순서: board1 -> board2
//                        boardService.toggleFavorite(boardId);
//                        Thread.sleep(10); // 약간의 지연으로 경쟁 조건 유발
//                        boardService.toggleFavorite(board2Id);
//                    } else {
//                        // 순서: board2 -> board1
//                        boardService.toggleFavorite(board2Id);
//                        Thread.sleep(10);
//                        boardService.toggleFavorite(boardId);
//                    }
//                    successCount.incrementAndGet();
//                } catch (Exception e) {
//                    if (e.getMessage() != null && e.getMessage().contains("deadlock")) {
//                        deadlockExceptions.incrementAndGet();
//                        log.warn("Deadlock detected and handled");
//                    } else {
//                        log.error("Unexpected exception: ", e);
//                    }
//                } finally {
//                    latch.countDown();
//                }
//            });
//        }
//
//        latch.await(20, TimeUnit.SECONDS);
//        executorService.shutdown();
//
//        // Then
//        log.info("Deadlock test - Success: {}, Deadlock exceptions: {}",
//                successCount.get(), deadlockExceptions.get());
//
//        // 데드락이 발생하더라도 시스템이 정상적으로 처리되어야 함
//        assertThat(successCount.get() + deadlockExceptions.get()).isEqualTo(numberOfThreads);
//    }
//
//    @Test
//    @DisplayName("메모리 사용량 모니터링을 포함한 대용량 동시성 테스트")
//    public void testMemoryUsageUnderHighConcurrency() throws Exception {
//        // Given
//        Runtime runtime = Runtime.getRuntime();
//        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
//
//        int numberOfThreads = MAX_THREADS;
//        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
//        CountDownLatch latch = new CountDownLatch(numberOfThreads);
//
//        log.info("Initial memory usage: {} MB", initialMemory / 1024 / 1024);
//
//        // When
//        for (int i = 0; i < numberOfThreads; i++) {
//            executorService.submit(() -> {
//                try {
//                    // 다양한 작업 수행
//                    boardService.readOne(boardId);
//                    boardService.toggleFavorite(boardId);
//                    boardService.readOne(boardId);
//                } finally {
//                    latch.countDown();
//                }
//            });
//        }
//
//        latch.await();
//        executorService.shutdown();
//
//        // Then
//        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
//        long memoryIncrease = finalMemory - initialMemory;
//
//        log.info("Final memory usage: {} MB", finalMemory / 1024 / 1024);
//        log.info("Memory increase: {} MB", memoryIncrease / 1024 / 1024);
//
//        BoardJpaEntity boardEntity = boardJpaRepository.findById(boardId).orElseThrow();
//        log.info("Final counts - Views: {}, Favorites: {}",
//                boardEntity.getView(), boardEntity.getFavorite());
//
//        // 메모리 사용량이 비정상적으로 증가하지 않았는지 확인 (임계값: 100MB)
//        assertThat(memoryIncrease).isLessThan(100 * 1024 * 1024);
//    }
}