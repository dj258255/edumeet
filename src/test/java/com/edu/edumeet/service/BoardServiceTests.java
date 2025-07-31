package com.edu.edumeet.service;

import com.edu.edumeet.board.infrastructure.BoardJpaEntity;
import com.edu.edumeet.board.infrastructure.BoardJpaRepository;
import com.edu.edumeet.board.presentation.BoardService;
import com.edu.edumeet.board.presentation.dto.*;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Log4j2
@ActiveProfiles("test")
public class BoardServiceTests {

    @Autowired
    private BoardService boardService;

    @Autowired
    private BoardJpaRepository boardJpaRepository;

    private Long testBoardId;
    private Long testBoardWithImagesId;

    public void 테스트_묵업데이터세팅(){
        boardJpaRepository.deleteAll();

        //테스트용 게시글
        createTestBoards();
    }

    private void createTestBoards(){
        //기본 게시글 생성( ID 101L 대신 사용)
        BoardJpaEntity board1 = BoardJpaEntity.builder()
                .title("Test Board 1")
                .content("Test Content 1")
                .writer("testUser1")
                .build();
        testBoardId = boardJpaRepository.save(board1).getId();

        // 이미지가 있는 게시글 생성 (ID 102L 대신 사용)
        BoardJpaEntity board2 = BoardJpaEntity.builder()
                .title("Test Board with Images")
                .content("Test Content with Images")
                .writer("testUser2")
                .build();

        // 이미지 추가
        for (int i = 0; i < 3; i++) {
            board2.addImage(UUID.randomUUID().toString(), "testfile" + i + ".jpg");
        }
        testBoardWithImagesId = boardJpaRepository.save(board2).getId();

        log.info("테스트 데이터 생성 완료. 기본 게시글 ID: {}, 이미지 게시글 ID: {}",
                testBoardId, testBoardWithImagesId);

    }

    @Test
    public void 테스트_가입_BoardService() {

        log.info(boardService.getClass().getName());

        BoardDTO boardDTO = BoardDTO.builder()
                .title("Sample Title...")
                .content("Sample Content...")
                .writer("user00")
                .build();

        Long board_id = boardService.register(boardDTO);
        log.info("board_id: " + board_id);
    }

    @Test
    public void 테스트_서비스수정() {
        // ID가 null인지 먼저 확인
        if (testBoardId == null) {
            log.error("testBoardId가 null입니다. 먼저 게시글을 등록하거나 유효한 ID를 설정해주세요.");
            return;
        }

        //실제 존재하는 게시글 ID 사용
        BoardDTO boardDTO = BoardDTO.builder()
                .id(testBoardId)
                .title("101게시글 업데이트")
                .content("101게시글 내용 변경")
                .build();


        boardService.modify(boardDTO);
        log.info("게시글 수정 완료. ID : " + testBoardId);

    }

    @Test
    public void 테스트_게시판리스트_Service() {

        PageRequestDTO pageRequestDTO = PageRequestDTO.builder()
                .type("tcw")
                .keyword("Test") //실제 존재하는 내용으로 검색하자.
                .page(1)
                .size(10)
                .build();

        PageResponseDTO<BoardDTO> responseDTO = boardService.list(pageRequestDTO);

        log.info(responseDTO);
    }



    @Test
    public void 테스트_게시판_이미지삽입(){
        log.info(boardService.getClass().getName());

        BoardDTO boardDTO = BoardDTO.builder()
                .title("파일 심플 제목")
                .content("샘플 내용")
                .writer("user00")
                .build();

        boardDTO.setFileNames(
                Arrays.asList(
                        UUID.randomUUID()+"_aaa.jpg",
                        UUID.randomUUID()+"_bbb.jpg",
                        UUID.randomUUID()+"_ccc.jpg"
                ));
        Long id = boardService.register(boardDTO);

        log.info("id : " + id);
    }
    
    //Board와 BoardImage들을 같이처리하는지확인
    @Test
    public void 테스트_게시판_게시판_같이_조회_처리하는지(){
        // 실제 존재하는 이미지가 있는 게시글 ID 사용
        BoardDTO boardDTO = boardService.readOne(testBoardWithImagesId);

        assertThat(boardDTO).isNotNull();
        log.info(boardDTO);

        if(boardDTO.getFileNames() != null) {
            for(String fileName : boardDTO.getFileNames()){
                log.info(fileName);
            }
        }
    }

    @Test
    public void 테스트_수정_이미지들삭제_새로운첨부파일하나(){
        //변경에 필요한 데이터
        BoardDTO boardDTO = BoardDTO.builder()
                .id(testBoardWithImagesId)
                .title("Updated....101")
                .content("Updated content 101...")
                .build();

        //첨부파일 하나 추가
        boardDTO.setFileNames(Arrays.asList(UUID.randomUUID()+"_zzz.jpg"));

        boardService.modify(boardDTO);
        log.info("이미지 수정 완료. ID : " + testBoardWithImagesId);
    }

    @Test
    public void 테스트_특정게시글_삭제(){
        // 실제 존재하는 게시글 ID 사용
        boardService.remove(testBoardId);
        log.info("게시글 삭제 완료. ID: " + testBoardId);

    }

    @Test
    public void 테스트_리스트_모든걸조회(){
        PageRequestDTO pageRequestDTO = PageRequestDTO.builder()
                .page(1)
                .size(10)
                .build();

        PageResponseDTO<BoardListAllDTO> responseDTO =
                boardService.listWithAll(pageRequestDTO);

        List<BoardListAllDTO> dtoList = responseDTO.getDtoList();

        dtoList.forEach(boardListAllDTO -> {
            log.info(boardListAllDTO.getId()+":"+boardListAllDTO.getTitle());

            if(boardListAllDTO.getBoardImages() != null){
                for(BoardImageDTO boardImage : boardListAllDTO.getBoardImages()){
                    log.info(boardImage);
                }
            }
            log.info("-----------------------------------------");
        });
    }
}