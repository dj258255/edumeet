package com.edu.edumeet.board.presentation;


import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.domain.BoardType;
import com.edu.edumeet.board.presentation.dto.*;
import com.edu.edumeet.s3.util.S3Uploader;
import com.edu.edumeet.upload.presentation.dto.FileUploadDTO;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 게시판 서비스 인터페이스
 * 애플리케이션 계층의 서비스로 도메인 모델을 사용하여 비즈니스 로직을 처리
 */
public interface BoardService {

    default Board dtoToDomain(BoardDTO boardDTO){
        // BoardType 설정
        BoardType boardTypeEnum = BoardType.NORMAL; // 기본값
        if (boardDTO.getBoardType() != null && !boardDTO.getBoardType().isEmpty()) {
            try {
                boardTypeEnum = BoardType.valueOf(boardDTO.getBoardType());
            } catch (IllegalArgumentException e) {
                // 잘못된 boardType이면 기본값 사용
                // 로그 추가 가능
            }
        }
        
        // 기본 Board 객체 생성
        Board board = Board.builder()
                .id(boardDTO.getId())
                .title(boardDTO.getTitle())
                .content(boardDTO.getContent())
                .writer(boardDTO.getWriter())
                .classId(boardDTO.getClassId())
                .categoryId(boardDTO.getCategoryId())
                .boardType(boardTypeEnum)
                .regDate(boardDTO.getRegDate())
                .modDate(boardDTO.getModDate())
                .view(boardDTO.getView())
                .favorite(boardDTO.getFavorite())
                .dislike(boardDTO.getDislike())
                .build();

        // FileUploadDTO 정보를 도메인 이미지로 변환
        if(boardDTO.getBoardImages() != null){
            for (FileUploadDTO imageDTO : boardDTO.getBoardImages()) {
                board.addImage(imageDTO.getUuid(), imageDTO.getFileName());
            }
        }
        
        return board;
    }

    /**
     * Board 도메인 객체를 BoardDTO로 변환
     * @param board 변환할 Board 도메인 객체
     * @param s3Uploader S3 URL 생성을 위한 S3Uploader
     * @return 변환된 BoardDTO 객체
     */
    default BoardDTO domainToDto(Board board, S3Uploader s3Uploader){
        BoardDTO boardDTO = BoardDTO.builder()
                .id(board.getId())
                .title(board.getTitle())
                .content(board.getContent())
                .writer(board.getWriter())
                .classId(board.getClassId())
                .categoryId(board.getCategoryId())
                .boardType(board.getBoardType() != null ? board.getBoardType().name() : null)
                .regDate(board.getRegDate())
                .modDate(board.getModDate())
                .view(board.getView())
                .favorite(board.getFavorite())
                .dislike(board.getDislike())
                .build();

        // 도메인 이미지를 FileUploadDTO로 변환
        if (board.getImages() != null && !board.getImages().isEmpty()) {
            List<FileUploadDTO> boardImages = board.getImages().stream()
                    .sorted()
                    .map(img -> {
                        String uuid = img.getUuid();
                        String fileName = img.getFileName();
                        
                        // S3Uploader를 사용하여 URL 생성
                        String s3Url = s3Uploader.getOriginalUrl(uuid, fileName);
                        String s3ThumbnailUrl = s3Uploader.getThumbnailUrl(uuid, fileName);
                        
                        return FileUploadDTO.builder()
                                .uuid(uuid)
                                .fileName(fileName)
                                .ord(img.getOrd())
                                .s3Url(s3Url)
                                .s3ThumbnailUrl(s3ThumbnailUrl)
                                .img(true)
                                .domain("board")
                                .referenceId(img.getReferenceId())
                                .build();
                    })
                    .collect(Collectors.toList());
            boardDTO.setBoardImages(boardImages);
        }

        return boardDTO;
    }





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
     * 게시글 좋아요 추가
     * @param id 좋아요 추가할 게시글 ID
     * @return 좋아요 추가 후 좋아요 수
     */
    long toggleFavorite(Long id);
    
    /**
     * 게시글 싫어요 추가
     * @param id 싫어요 추가할 게시글 ID
     * @return 싫어요 추가 후 싫어요 수
     */
    long toggleDislike(Long id);
    
    /**
     * 삭제된 게시글 복원
     * @param id 복원할 게시글 ID
     */
    void restore(Long id);
}
