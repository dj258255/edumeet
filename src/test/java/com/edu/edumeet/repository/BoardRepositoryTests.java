package com.edu.edumeet.repository;


import com.edu.edumeet.board.application.BoardSearchRepository;
import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.infrastructure.BoardImageJpaEntity;
import com.edu.edumeet.board.infrastructure.BoardJpaEntity;
import com.edu.edumeet.board.infrastructure.BoardJpaRepository;
import com.edu.edumeet.board.presentation.dto.BoardListAllDTO;
import com.edu.edumeet.board.presentation.dto.BoardListReplyCountDTO;
import com.edu.edumeet.reply.infrastructure.ReplyJpaRepository;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Log4j2
@Transactional
@ActiveProfiles("test")
public class BoardRepositoryTests {

    @Autowired
    private BoardJpaRepository boardJpaRepository;

    @Autowired
    private BoardSearchRepository boardSearchRepository;

    @Autowired
    private ReplyJpaRepository replyJpaRepository;

//    @Autowired
//    private BoardService boardService;

    private Long testBoardId;

    @BeforeEach
    public void 테스트_더미데이터_100개씩(){
        // 기존 데이터 삭제
        replyJpaRepository.deleteAll();
        boardJpaRepository.deleteAll();

        for(int i = 1; i <= 100; i++){
            Board.BoardBuilder boardBuilder = Board.builder()
                    .title("title..." +i)
                    .content("content..." + i)
                    .writer("user"+ (i % 10))
                    .classId(1L);

            Board board = boardBuilder.build();

            //도메인로직으로 이미지 추가
            for(int j = 0; j < 3; j++){

                if(i % 5 ==0){
                    continue;
                }
                board.addImage(UUID.randomUUID().toString(), "file" + i + ".jpg");
            }

            //도메인 모델을 jpa 엔티티로 변환 후 저장

            BoardJpaEntity savedEntity = boardJpaRepository.save(BoardJpaEntity.fromDomain(board));

            if(i==1){
                testBoardId = savedEntity.getId();
            }
        }
        log.info("테스트 데이터 생성 완료 : 첫 번째 게시글 ID :  " + testBoardId);
    }

//    @Test
//    public void 게시글100개씩넣기() {
//        IntStream.rangeClosed(1,100).forEach(i -> {
//            BoardJpaEntity boardJpaEntity = BoardJpaEntity.builder()
//                    .title("title..." +i)
//                    .content("content..." + i)
//                    .writer("user"+ (i % 10))
//                    .build();
//
//            //jpa엔티티를 도메인으로 변환.
//            Board result = boardJpaRepository.save(boardJpaEntity).toModel();
//            log.info("board_id: " + result.getId());
//        });
//    }

    @Test
    public void 테스트_게시글정보보기() {
        Optional<BoardJpaEntity> result = boardJpaRepository.findById(testBoardId);
        BoardJpaEntity boardJpaEntity = result.orElseThrow();

        log.info("게시글 정보 : " + boardJpaEntity);
        log.info("이미지 개수 : " + boardJpaEntity.getImageSet().size());
    }

    @Test
    public void 게시글검색시_댓글개수_카운트되는지() {

        String[] types = {"t", "c", "w"};

        String keyword = "1";

        Pageable pageable = PageRequest.of(1, 10, Sort.by("id").descending());

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
        Board board = Board.builder()
                .title("이미지 테스트")
                .content("첨부파일 테스트")
                .writer("테스터")
                .classId(1L)
                .build();

        for (int i = 0; i < 3; i++) {
            board.addImage(UUID.randomUUID().toString(), "file" + i + ".jpg");
        }

        BoardJpaEntity savedEntity = boardJpaRepository.save(BoardJpaEntity.fromDomain(board));
        log.info("Saved board id: " + savedEntity.getId());
        log.info("이미지 개수 : " + savedEntity.getImageSet().size());
    }


    //OneToMany의 로딩방식은 기본적으로 lazy로딩. 게시물 조회하는 경우 Board 객체와 BoardImage 객체들을 생성해야하니
    //2번의 select가 필요하게 된다. 그걸 확인하기 위한 test코드다.
    //이를 해결하기 위해선 Transactional을 추가해야 하는 거다. 그걸 적용하면 필요할 때마다
    //메소드 내에서 추가적인 쿼리를 여러 번 실행하는 것이 가능해지기 때문이다.
    @Test
    public void 이미지와함께_게시글읽기(){
        // findById 대신 findByIdWithImages 사용
        Optional<BoardJpaEntity> result = boardJpaRepository.findByIdWithImages(testBoardId);
        
        BoardJpaEntity boardJpaEntity = result.orElseThrow(()->
                new RuntimeException("게시글을 찾을 수 없습니다. ID: " + testBoardId));

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
    public void 테스트_이미지수정(){
        Optional<BoardJpaEntity> result = boardJpaRepository.findByIdWithImages(testBoardId);
        BoardJpaEntity boardJpaEntity = result.orElseThrow();

        Board board = boardJpaEntity.toModel();

        //기존의 첨부파일들은 삭제
        board.clearImages();

        //새로운 첨부파일들
        for(int i = 0 ; i < 2; i++){
            board.addImage(UUID.randomUUID().toString(), "file" + i + ".jpg");
        }

        boardJpaEntity.updateFromDomain(board);
        boardJpaRepository.save(boardJpaEntity);

        log.info("수정된 이미지 개수 : " + boardJpaEntity.getImageSet().size());

    }


    @Test
    @Transactional
    @Commit
    public void 테스트_게시글댓글함께삭제(){
        Long board_id = testBoardId;

        replyJpaRepository.deleteByBoard_Id(board_id);
        log.info("댓글 삭제 성공~");

        boardJpaRepository.deleteById(board_id);
        log.info("게시글 삭제 성공~");
    }





//    //N+1이 발생함 보통은.
//    @Transactional
//    @Test
//    public void 테스트_검색_이미지_댓글개수(){
//        Pageable pageable = PageRequest.of(0,10,Sort.by("id").descending());
//
//        Page<BoardListAllDTO> result = boardSearchRepository.searchWithAll(null, null, pageable);
//
//        log.info("조회된 게시글 수: " + result.getContent().size());
//        log.info("전체 게시글 수 : " + result.getTotalElements());
//
//        result.getContent().forEach(dto -> log.info(dto));
//
//    }

    @Transactional
    @Test
    public void 테스트_검색_이미지_댓글개수(){
        Pageable pageable = PageRequest.of(0,10,Sort.by("id").descending());

        // ✨ classId 파라미터 추가 (null = 전체 조회)
        Page<BoardListAllDTO> result = boardSearchRepository.searchWithAll(null, null, null, pageable);

        log.info("조회된 게시글 수: " + result.getContent().size());
        log.info("전체 게시글 수 : " + result.getTotalElements());

        result.getContent().forEach(dto -> {
            log.info("ID: {}, 제목: {}, 클래스: {}, 댓글수: {}, 이미지수: {}",
                    dto.getId(), dto.getTitle(), dto.getClassId(), dto.getReplyCount(),
                    dto.getBoardImages() != null ? dto.getBoardImages().size() : 0);
        });
    }

    @Test
    public void 테스트_클래스별_튜플_검색(){
        // 클래스 2 데이터 추가
        for(int i = 101; i <= 110; i++){
            Board board = Board.builder()
                    .title("클래스2_title..." + i)
                    .content("클래스2_content..." + i)
                    .writer("클래스2_user" + (i % 5))
                    .classId(2L)
                    .build();

            // 이미지도 추가
            if(i % 3 != 0) {
                board.addImage(UUID.randomUUID().toString(), "class2_file" + i + ".jpg");
            }

            boardJpaRepository.save(BoardJpaEntity.fromDomain(board));
        }

        Pageable pageable = PageRequest.of(0, 5, Sort.by("id").descending());

        // 클래스 1만 조회
        Page<BoardListAllDTO> result1 = boardSearchRepository
                .searchWithAll(null, null, 1L, pageable);

        // 클래스 2만 조회
        Page<BoardListAllDTO> result2 = boardSearchRepository
                .searchWithAll(null, null, 2L, pageable);

        log.info("클래스 1 게시글 수: {}", result1.getTotalElements());
        log.info("클래스 2 게시글 수: {}", result2.getTotalElements());

        // 검증
        result1.getContent().forEach(dto -> {
            assertThat(dto.getClassId()).isEqualTo(1L);
            log.info("클래스1 - ID: {}, 제목: {}, 댓글: {}",
                    dto.getId(), dto.getTitle(), dto.getReplyCount());
        });

        result2.getContent().forEach(dto -> {
            assertThat(dto.getClassId()).isEqualTo(2L);
            log.info("클래스2 - ID: {}, 제목: {}, 댓글: {}",
                    dto.getId(), dto.getTitle(), dto.getReplyCount());
        });
    }

    @Test
    public void 테스트_클래스별_키워드_검색(){
        // 클래스 1에서 title로 검색
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id").descending());

        Page<BoardListAllDTO> result = boardSearchRepository
                .searchWithAll(new String[]{"t"}, "title", 1L, pageable);

        log.info("클래스 1에서 'title' 검색 결과: {}건", result.getTotalElements());

        result.getContent().forEach(dto -> {
            assertThat(dto.getClassId()).isEqualTo(1L);
            assertThat(dto.getTitle().toLowerCase()).contains("title");
            log.info("검색결과 - ID: {}, 제목: {}, 클래스: {}",
                    dto.getId(), dto.getTitle(), dto.getClassId());
        });
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

//    @Transactional
//    @Test
//    public void 테스트_검색_이미지_댓글_카운팅(){
//        Pageable pageable = PageRequest.of(0,10,Sort.by("id").descending());
//
//        //BoardRepository.searchWithAll(null, null , pageable);
//
//        Page<BoardListAllDTO> result = boardSearchRepository.searchWithAll(null,null,pageable);
//
//        log.info("-----------------------------------------");
//        log.info(result.getTotalElements());
//
//        result.getContent().forEach(boardListAllDTO -> log.info(boardListAllDTO));
//    }




}