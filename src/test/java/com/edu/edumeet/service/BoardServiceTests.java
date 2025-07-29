package com.edu.edumeet.service;

import com.edu.edumeet.board.presentation.BoardService;
import com.edu.edumeet.board.presentation.dto.*;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@SpringBootTest
@Log4j2
public class BoardServiceTests {

    @Autowired
    private BoardService boardService;

    @Test
    public void testRegister() {

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
    public void testModify() {

        //변경에 필요한 데이터만
        BoardDTO boardDTO = BoardDTO.builder()
                .id(101L)
                .title("Updated....101")
                .content("Updated content 101...")
                .build();

        boardService.modify(boardDTO);

    }

    @Test
    public void testList() {

        PageRequestDTO pageRequestDTO = PageRequestDTO.builder()
                .type("tcw")
                .keyword("1")
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
        Long id = 102L;

        BoardDTO boardDTO = boardService.readOne(id);

        log.info(boardDTO);

        for(String fileName : boardDTO.getFileNames()){
            log.info(fileName);
        }
    }

    @Test
    public void 테스트_수정_이미지들삭제_새로운첨부파일하나(){
        //변경에 필요한 데이터
        BoardDTO boardDTO = BoardDTO.builder()
                .id(102L)
                .title("Updated....101")
                .content("Updated content 101...")
                .build();

        //첨부파일 하나 추가
        boardDTO.setFileNames(Arrays.asList(UUID.randomUUID()+"_zzz.jpg"));

        boardService.modify(boardDTO);
    }

    @Test
    public void 테스트_특정게시글_삭제(){
        Long id = 1L;

        boardService.remove(id);
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