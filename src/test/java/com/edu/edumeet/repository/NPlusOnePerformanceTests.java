package com.edu.edumeet.repository;

import com.edu.edumeet.board.infrastructure.BoardJpaEntity;
import com.edu.edumeet.board.infrastructure.BoardJpaRepository;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * JPA N+1 문제 해결 방법 비교 테스트
 */
@SpringBootTest
@Log4j2
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class NPlusOnePerformanceTests {

    @Autowired
    private BoardJpaRepository boardJpaRepository;

    @Test
    @Order(0)
    public void 테스트_데이터_생성() {
        if (boardJpaRepository.count() >= 50) return;
        
        boardJpaRepository.deleteAll();
        
        IntStream.rangeClosed(1, 50).forEach(i -> {
            BoardJpaEntity board = BoardJpaEntity.builder()
                    .title("게시글 " + i)
                    .content("내용 " + i)
                    .writer("user" + (i % 5))
                    .build();

            for (int j = 0; j < 3; j++) {
                board.addImage(UUID.randomUUID().toString(), "img" + i + "_" + j + ".jpg");
            }
            
            boardJpaRepository.save(board);
        });
        
        System.out.println("✅ 테스트 데이터 준비 완료\n");
    }

    @Test
    @Order(1)
    @Transactional
    public void 방법1_N플러스1_문제() {
        
        Pageable pageable = PageRequest.of(0, 5);
        Page<BoardJpaEntity> boards = boardJpaRepository.findAll(pageable);
        
        System.out.println("게시글 조회: " + boards.getContent().size() + "개");
        
        boards.getContent().forEach(board -> {
            int count = board.getImageSet().size(); // 🔥 각각 쿼리!
            System.out.println("게시글 " + board.getId() + ": " + count + "개 이미지");
        });
    }

    @Test
    @Order(2)
    @Transactional
    public void 방법2_Fetch_Join() {
        
        List<Long> ids = List.of(1L, 2L, 3L, 4L, 5L);
        List<BoardJpaEntity> boards = boardJpaRepository.findByIdsWithImagesFetchJoin(ids);
        
        System.out.println("게시글 조회: " + boards.size() + "개");
        
        boards.forEach(board -> {
            System.out.println("게시글 " + board.getId() + ": " + board.getImageSet().size() + "개 이미지");
        });

    }

    @Test
    @Order(3)
    @Transactional
    public void 방법3_EntityGraph() {
        List<Long> ids = List.of(1L, 2L, 3L, 4L, 5L);
        List<BoardJpaEntity> boards = boardJpaRepository.findByIdsWithEntityGraph(ids);
        
        System.out.println("게시글 조회: " + boards.size() + "개");
        
        boards.forEach(board -> {
            System.out.println("게시글 " + board.getId() + ": " + board.getImageSet().size() + "개 이미지");
        });
    }

    @Test
    @Order(4)
    @Transactional
    public void 방법4_BatchSize() {
        // 이 테스트를 위해서는 엔티티에서 @Fetch(SUBSELECT) 대신 @BatchSize 필요
        
        Pageable pageable = PageRequest.of(0, 15);
        Page<BoardJpaEntity> boards = boardJpaRepository.findAll(pageable);
        
        System.out.println("게시글 조회: " + boards.getContent().size() + "개");
        
        boards.getContent().forEach(board -> {
            int count = board.getImageSet().size();
            System.out.println("게시글 " + board.getId() + ": " + count + "개 이미지");
        });
    }

    @Test
    @Order(5)
    @Transactional
    public void 방법5_Fetch_SUBSELECT() {
        
        // ⚠️ 현재 엔티티에 @Fetch(SUBSELECT) 설정되어 있어야 함
        
        Pageable pageable = PageRequest.of(0, 10);
        Page<BoardJpaEntity> boards = boardJpaRepository.findAll(pageable);
        
        System.out.println("게시글 조회: " + boards.getContent().size() + "개");
        
        boards.getContent().forEach(board -> {
            int count = board.getImageSet().size(); // 서브쿼리로 한번에!
            System.out.println("게시글 " + board.getId() + ": " + count + "개 이미지");
        });
    }
}