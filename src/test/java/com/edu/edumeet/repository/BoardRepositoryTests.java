package com.edu.edumeet.repository;


import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.infrastructure.BoardJpaEntity;
import com.edu.edumeet.board.infrastructure.BoardJpaRepository;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;
import java.util.stream.IntStream;

@SpringBootTest
@Log4j2
public class BoardRepositoryTests {

    @Autowired
    private BoardJpaRepository boardJpaRepository;

    @Test
    public void testInsert() {
        IntStream.rangeClosed(1,100).forEach(i -> {
            BoardJpaEntity boardJpaEntity = BoardJpaEntity.builder()
                    .title("title..." +i)
                    .content("content..." + i)
                    .writer("user"+ (i % 10))
                    .build();

            //jpa엔티티를 도메인으로 변환.
            Board result = boardJpaRepository.save(boardJpaEntity).toModel();
            log.info("BNO: " + result.getId());
        });
    }

    @Test
    public void testSelect() {
        Long id = 100L;

        Optional<BoardJpaEntity> result = boardJpaRepository.findById(id);

        Board board = result.orElseThrow().toModel();

        log.info(board);
    }
}