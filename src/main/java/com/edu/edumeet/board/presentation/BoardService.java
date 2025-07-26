package com.edu.edumeet.board.presentation;


import com.edu.edumeet.board.presentation.dto.BoardDTO;
import com.edu.edumeet.board.presentation.dto.BoardListReplyCountDTO;
import com.edu.edumeet.board.presentation.dto.PageRequestDTO;
import com.edu.edumeet.board.presentation.dto.PageResponseDTO;

/**
 * 게시판 서비스 인터페이스
 * 애플리케이션 계층의 서비스로 도메인 모델을 사용하여 비즈니스 로직을 처리
 */
public interface BoardService {

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
}
