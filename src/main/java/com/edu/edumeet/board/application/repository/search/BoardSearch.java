package com.edu.edumeet.board.application.repository.search;

import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.presentation.dto.BoardListReplyCountDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BoardSearch {

    /**
     * 타입과 키워드로 게시글 검색
     * @param types 검색 타입 (t: 제목, c: 내용, w: 작성자)
     * @param keyword 검색 키워드
     * @param pageable 페이징 정보
     * @return 검색 결과
     */
    Page<Board> searchAll(String[] types, String keyword, Pageable pageable);

    /**
     * 게시글과 댓글 수를 함께 조회
     * @param types 검색 타입 (t: 제목, c: 내용, w: 작성자)
     * @param keyword 검색 키워드
     * @param pageable 페이징 정보
     * @return 게시글과 댓글 수 정보
     */
    Page<BoardListReplyCountDTO> searchWithReplyCount(String[] types, String keyword, Pageable pageable);

}
