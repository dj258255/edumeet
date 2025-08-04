package com.edu.edumeet.board.presentation;


import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.infrastructure.BoardJpaEntity;
import com.edu.edumeet.board.presentation.dto.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 게시판 서비스 인터페이스
 * 애플리케이션 계층의 서비스로 도메인 모델을 사용하여 비즈니스 로직을 처리
 */
public interface BoardService {

    default Board dtoToDomain(BoardDTO boardDTO){
        Board board = Board.builder()
                .id(boardDTO.getId())
                .title(boardDTO.getTitle())
                .content(boardDTO.getContent())
                .writer(boardDTO.getWriter())
                .classId(boardDTO.getClassId())
                .regDate(boardDTO.getRegDate())
                .modDate(boardDTO.getModDate())
                .view(boardDTO.getView())
                .favorite(boardDTO.getFavorite())
                .build();

        // 파일 정보를 도메인 이미지로 변환
        if(boardDTO.getFileNames() != null){
            boardDTO.getFileNames().forEach(fileName -> {
                String[] arr = fileName.split("_");
                board.addImage(arr[0], arr[1]);
            });
        }
        return board;
    }

    /**
     * Board 도메인 객체를 BoardDTO로 변환
     */
    default BoardDTO domainToDto(Board board){
        BoardDTO boardDTO = BoardDTO.builder()
                .id(board.getId())
                .title(board.getTitle())
                .content(board.getContent())
                .writer(board.getWriter())
                .classId(board.getClassId())
                .regDate(board.getRegDate())
                .modDate(board.getModDate())
                .view(board.getView())
                .favorite(board.getFavorite())
                .build();

        // 도메인 이미지를 파일명으로 변환
        if (board.getImages() != null && !board.getImages().isEmpty()) {
            List<String> fileNames = board.getImages().stream()
                    .sorted()
                    .map(img -> img.getUuid() + "_" + img.getFileName())
                    .collect(Collectors.toList());
            boardDTO.setFileNames(fileNames);
        }

        return boardDTO;
    }


//
//    //default 메소드
//    default BoardJpaEntity dtoToEntity(BoardDTO boardDTO){
//        BoardJpaEntity boardJpaEntity = BoardJpaEntity.builder()
//                .id(boardDTO.getId())
//                .title(boardDTO.getTitle())
//                .content(boardDTO.getContent())
//                .writer(boardDTO.getWriter())
//                .build();
//
//        if(boardDTO.getFileNames() != null){
//            boardDTO.getFileNames().forEach(fileName -> {
//                String[] arr = fileName.split("_");
//                boardJpaEntity.addImage(arr[0], arr[1]);
//            });
//        }
//        return boardJpaEntity;
//    }
//
//    default BoardDTO entityToDto(BoardJpaEntity boardJpaEntity){
//        BoardDTO boardDTO = BoardDTO.builder()
//                .id(boardJpaEntity.getId())
//                .title(boardJpaEntity.getTitle())
//                .content(boardJpaEntity.getContent())
//                .writer(boardJpaEntity.getWriter())
//                .regDate(boardJpaEntity.getRegDate())
//                .modDate(boardJpaEntity.getModDate())
//                .build();
//
//        List<String> fileNames =
//                boardJpaEntity.getImageSet().stream().sorted().map(boardImageJpaEntity ->
//                        boardImageJpaEntity.getUuid()+"_"+boardImageJpaEntity.getFilename()).collect(Collectors.toList());
//
//        boardDTO.setFileNames(fileNames);
//
//        return boardDTO;
//    }



    public void addImageToBoard(Long boardId, String uuid, String fileName);


    /**
     * 게시글 등록
     * @param boardDTO 등록할 게시글 정보
     * @return 등록된 게시글의 ID
     */
    Long register(BoardDTO boardDTO);

    /**
     * 게시글 조회
     * @param id 조회할 게시글 ID
     * @return 조회된 게시글 정보
     */
    BoardDTO readOne(Long id);

    /**
     * 게시글 수정
     * @param boardDTO 수정할 게시글 정보
     */
    void modify(BoardDTO boardDTO);

    /**
     * 게시글 삭제
     * @param id 삭제할 게시글 ID
     */
    void remove(Long id);

    /**
     * 게시글 목록 조회
     * @param pageRequestDTO 페이징 및 검색 조건
     * @return 게시글 목록 및 페이징 정보
     */
    PageResponseDTO<BoardDTO> list(PageRequestDTO pageRequestDTO);

    /**
     * 댓글 수가 포함된 게시글 목록 조회
     * @param pageRequestDTO 페이징 및 검색 조건
     * @return 게시글 목록, 댓글 수 및 페이징 정보
     */
    PageResponseDTO<BoardListReplyCountDTO> listWithReplyCount(PageRequestDTO pageRequestDTO);

    //게시글의 이미지와 댓글의 숫자까지 처리
    // classId가 있으면 해당 클래스의 게시글만, 없으면 전체 게시글 조회
    // return : 게시글 목록 , 이미지 , 댓글 수 및 페이징 정보.
    PageResponseDTO<BoardListAllDTO> listWithAll(PageRequestDTO pageRequestDTO);
    
    /**
     * 게시글 좋아요 토글
     * 이미 좋아요가 되어 있으면 좋아요 취소, 아니면 좋아요 추가
     * @param id 좋아요 토글할 게시글 ID
     * @return 토글 후 좋아요 수
     */
    long toggleFavorite(Long id);
}
