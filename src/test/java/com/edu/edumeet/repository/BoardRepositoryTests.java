package com.edu.edumeet.repository;


import com.edu.edumeet.board.application.BoardSearchRepository;
import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.infrastructure.BoardImageJpaEntity;
import com.edu.edumeet.board.infrastructure.BoardJpaEntity;
import com.edu.edumeet.board.infrastructure.BoardJpaRepository;
import com.edu.edumeet.board.presentation.BoardService;
import com.edu.edumeet.board.presentation.dto.BoardDTO;
import com.edu.edumeet.board.presentation.dto.BoardListAllDTO;
import com.edu.edumeet.board.presentation.dto.BoardListReplyCountDTO;
import com.edu.edumeet.reply.infrastructure.ReplyJpaRepository;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Log4j2
public class BoardRepositoryTests {

    @Autowired
    private BoardJpaRepository boardJpaRepository;

    @Autowired
    private BoardSearchRepository boardSearchRepository;

    @Autowired
    private ReplyJpaRepository replyJpaRepository;
    @Autowired
    private BoardService boardService;

    @Test
    public void 게시글100개씩넣기() {
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
    public void 게시글정보보기() {
        Long id = 100L;

        Optional<BoardJpaEntity> result = boardJpaRepository.findById(id);

        Board board = result.orElseThrow().toModel();

        log.info(board);
    }

    @Test
    public void 게시글검색시_댓글개수_카운트되는지() {

        String[] types = {"t", "c", "w"};

        String keyword = "1";

        Pageable pageable = PageRequest.of(2, 10, Sort.by("id").descending());

        Page<BoardListReplyCountDTO> result = boardSearchRepository
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
    public void 이미지와게시판_Insert되는지() {
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
    public void 이미지와함께_게시글읽기(){
        // findById 대신 findByIdWithImages 사용
        Optional<BoardJpaEntity> result = boardJpaRepository.findByIdWithImages(1L);
        
        BoardJpaEntity boardJpaEntity = result.orElseThrow();

        log.info(boardJpaEntity);
        log.info("------------------");
        for(BoardImageJpaEntity boardImageJpaEntity : boardJpaEntity.getImageSet()){
            log.info(boardImageJpaEntity);
        }
    }


    /**
     * 현재 cascade 속성이 ALL로 지정되었기 때문에 상위 엔티티(Board)의 상태 변화가 하위
     *엔티티의 참조가 더 이상 없는 상태가 되면 @OneToMany에 orphanRemoval 속성값을 트루로 지정해 주어야만 실제 삭제가 이뤄진다.
     * board 클래스의 @OneToMany 속성을 다음과 같이 조정한다.
     * orphanRemoval = true로 지정한다.
     *
     * 그 다음 다시 테스트 코드를 수정해보면
     * 추가된 BoardImage가 insert 되고 기존의 이미지들은 delete 되는 것을 확인할 수 있다.
     */

    @Transactional
    @Commit
    @Test
    public void 이미지수정_테스트(){
        Optional<BoardJpaEntity> result = boardJpaRepository.findByIdWithImages(1L);

        BoardJpaEntity boardJpaEntity = result.orElseThrow();

        //기존의 첨부파일들은 삭제
        boardJpaEntity.clearImages();

        //새로운 첨부파일들
        for(int i=0; i< 2;i++){

            boardJpaEntity.addImage(UUID.randomUUID().toString() , "updatefile"+i+".jpg");
        }

        boardJpaRepository.save(boardJpaEntity);
    }


    @Test
    @Transactional
    @Commit
    public void 테스트_게시글댓글함께삭제(){
        Long board_id = 1L;

        replyJpaRepository.deleteByBoard_Id(board_id);
        log.info("댓글 삭제 성공~");

        boardJpaRepository.deleteById(board_id);
        log.info("게시글 삭제 성공~");
    }

    @Test
    public void 테스트_더미데이터_100개씩(){
        for(int i = 1; i <= 100; i++){
            BoardJpaEntity boardJpaEntity = BoardJpaEntity.builder()
                    .title("title..." +i)
                    .content("content..." + i)
                    .writer("user"+ (i % 10))
                    .build();

            for(int j = 0; j < 3; j++){

                if(i % 5 ==0){
                    continue;
                }
                boardJpaEntity.addImage(UUID.randomUUID().toString(), "file" + i + ".jpg");
            }
            boardJpaRepository.save(boardJpaEntity);
        }
    }



    //N+1이 발생함 보통은.
    @Transactional
    @Test
    public void 테스트_검색_이미지_댓글개수(){
        Pageable pageable = PageRequest.of(0,10,Sort.by("id").descending());
        
        boardSearchRepository.searchWithAll(null, null, pageable);
    }



// FetchJoin 실험용 테스트
//    @Transactional
//    @Test
//    public void 테스트_FetchJoin_검색_이미지_댓글개수(){
//        Pageable pageable = PageRequest.of(0,10,Sort.by("id").descending());
//
//        log.info("==== FetchJoin 테스트 시작 ====");
//        Page<BoardJpaEntity> result = boardJpaRepository.searchWithAllFetchJoin(pageable);
//
//        log.info("조회된 게시글 수: " + result.getContent().size());
//        log.info("전체 게시글 수: " + result.getTotalElements());
//
//        result.getContent().forEach(board -> {
//            log.info("Board ID: " + board.getId() + ", Title: " + board.getTitle() +
//                ", Writer: " + board.getWriter());
//            log.info("이미지 개수: " + board.getImageSet().size());
//            log.info("-------------------");
//        });
//
//        log.info("==== FetchJoin 테스트 완료 ====");
//    }

//
//      실험용 코드.
//    @Transactional
//    @Test
//    public void 테스트_EntityGraph_검색_이미지(){
//        Pageable pageable = PageRequest.of(0,10,Sort.by("id").descending());
//
//        log.info("==== EntityGraph 테스트 시작 ====");
//        Page<BoardJpaEntity> result = boardJpaRepository.searchWithAllEntityGraph(pageable);
//
//        log.info("조회된 게시글 수: " + result.getContent().size());
//        log.info("전체 게시글 수: " + result.getTotalElements());
//
//        result.getContent().forEach(board -> {
//            log.info("Board ID: " + board.getId() + ", Title: " + board.getTitle() +
//                    ", Writer: " + board.getWriter());
//            log.info("이미지 개수: " + board.getImageSet().size());
//            log.info("-------------------");
//        });
//
//        log.info("==== EntityGraph 테스트 완료 ====");
//    }


//
//
//    @Transactional
//    @Test
//    public void 테스트_SubSelect_검색_이미지(){
//        Pageable pageable = PageRequest.of(0,10,Sort.by("id").descending());
//
//        log.info("==== SubSelect 테스트 시작 ====");
//        Page<BoardJpaEntity> result = boardJpaRepository.searchWithAllSubSelect(pageable);
//
//        log.info("조회된 게시글 수: " + result.getContent().size());
//        log.info("전체 게시글 수: " + result.getTotalElements());
//
//        result.getContent().forEach(board -> {
//            log.info("Board ID: " + board.getId() + ", Title: " + board.getTitle() +
//                    ", Writer: " + board.getWriter());
//            log.info("이미지 개수: " + board.getImageSet().size());
//            log.info("-------------------");
//        });
//
//        log.info("==== SubSelect 테스트 완료 ====");
//    }

//    @Transactional
//    @Test
//    public void 테스트_검색_이미지_댓글개수_배치사이즈(){
//        Pageable pageable = PageRequest.of(0,10,Sort.by("id").descending());
//
//        log.info("==== BatchSize 테스트 시작 ====");
//
//        // searchWithAll 메서드 호출 (실제로는 null 반환)
//        boardJpaRepository.searchWithAll(null, null, pageable);
//
//        // 직접 데이터를 조회해서 로그 출력
//        Page<BoardJpaEntity> result = boardJpaRepository.findAll(pageable);
//
//        log.info("조회된 게시글 수: " + result.getContent().size());
//        log.info("전체 게시글 수: " + result.getTotalElements());
//
//        result.getContent().forEach(board -> {
//            log.info("Board ID: " + board.getId() + ", Title: " + board.getTitle() +
//                    ", Writer: " + board.getWriter());
//            log.info("이미지 개수: " + board.getImageSet().size()); // 여기서 BatchSize 실행
//            log.info("-------------------");
//        });
//
//        log.info("==== BatchSize 테스트 완료 ====");
//    }

    @Transactional
    @Test
    public void testSearchImageReplyCount(){
        Pageable pageable = PageRequest.of(0,10,Sort.by("id").descending());

        //BoardRepository.searchWithAll(null, null , pageable);

        Page<BoardListAllDTO> result = boardSearchRepository.searchWithAll(null,null,pageable);

        log.info("-----------------------------------------");
        log.info(result.getTotalElements());

        result.getContent().forEach(boardListAllDTO -> log.info(boardListAllDTO));
    }


}