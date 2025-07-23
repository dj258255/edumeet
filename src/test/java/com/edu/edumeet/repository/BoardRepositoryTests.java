package com.edu.edumeet.repository;


import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.infrastructure.BoardImageJpaEntity;
import com.edu.edumeet.board.infrastructure.BoardJpaEntity;
import com.edu.edumeet.board.infrastructure.BoardJpaRepository;
import com.edu.edumeet.board.presentation.dto.BoardListReplyCountDTO;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Optional;
import java.util.UUID;
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
            log.info("board_id: " + result.getId());
        });
    }

    @Test
    public void testSelect() {
        Long id = 100L;

        Optional<BoardJpaEntity> result = boardJpaRepository.findById(id);

        Board board = result.orElseThrow().toModel();

        log.info(board);
    }

    @Test
    public void testSearchReplyCount() {

        String[] types = {"t", "c", "w"};

        String keyword = "1";

        Pageable pageable = PageRequest.of(2, 10, Sort.by("id").descending());

        Page<BoardListReplyCountDTO> result = boardJpaRepository
                .searchWithReplyCount(types, keyword, pageable);

        //전체 페이지 
        log.info("Total Pages: " + result.getTotalPages());

        //페이지 크기
        log.info("Page Size: " + result.getSize());

        //페이지 번호 
        log.info("Page Number: " + result.getNumber());

        //이전 다음
        log.info("Has Previous: " + result.hasPrevious() + " Has Next: " + result.hasNext());

        result.getContent().forEach(board -> {
            log.info("Board ID: " + board.getId());
            log.info("Title: " + board.getTitle());
            log.info("Writer: " + board.getWriter());
            log.info("Reply Count: " + board.getReplyCount());
            log.info("-------------------");
        });
    }


    //게시물 추가 + 첨부파일 삽입

    @Test
    public void testInsertWithImages() {
        BoardJpaEntity boardJpaEntity = BoardJpaEntity.builder()
                .title("Image Test")
                .content("첨부파일 테스트")
                .writer("tester")
                .build();

        for (int i = 0; i < 3; i++) {
            boardJpaEntity.addImage(UUID.randomUUID().toString(), "file" + i + ".jpg");
        }

        BoardJpaEntity saved = boardJpaRepository.save(boardJpaEntity);
        log.info("Saved board id: " + saved.getId());
    }


    //OneToMany의 로딩방식은 기본적으로 lazy로딩. 게시물 조회하는 경우 Board 객체와 BoardImage 객체들을 생성해야하니
    //2번의 select가 필요하게 된다. 그걸 확인하기 위한 test코드다.
    //이를 해결하기 위해선 Transactional을 추가해야 하는 거다. 그걸 적용하면 필요할 때마다
    //메소드 내에서 추가적인 쿼리를 여러 번 실행하는 것이 가능해지기 때문이다.
    @Test
    public void testReadWithImages(){
        // findById 대신 findByIdWithImages 사용
        Optional<BoardJpaEntity> result = boardJpaRepository.findByIdWithImages(1L);
        
        BoardJpaEntity boardJpaEntity = result.orElseThrow();

        log.info(boardJpaEntity);
        log.info("------------------");
        for(BoardImageJpaEntity boardImageJpaEntity : boardJpaEntity.getImageSet()){
            log.info(boardImageJpaEntity);
        }
    }
}