package com.edu.edumeet.board.repository;

import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.dto.BoardListAllDTO;
import com.edu.edumeet.board.dto.BoardListReplyCountDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BoardSearchRepository {

    /**
     * 타입과 키워드로 게시글 검색 (기본적으로 삭제되지 않은 게시글만 검색)
     * @param types 검색 타입 (t: 제목, c: 내용, w: 작성자)
     * @param keyword 검색 키워드
     * @param pageable 페이징 정보
     * @return 검색 결과
     */
    Page<Board> searchAll(String[] types, String keyword, Pageable pageable);
    
    /**
     * 타입과 키워드로 삭제된 게시글 검색
     * @param types 검색 타입 (t: 제목, c: 내용, w: 작성자)
     * @param keyword 검색 키워드
     * @param pageable 페이징 정보
     * @return 검색 결과
     */
    Page<Board> searchAllDeleted(String[] types, String keyword, Pageable pageable);

    /**
     * 게시글과 댓글 수를 함께 조회 (기본적으로 삭제되지 않은 게시글만 검색)
     * @param types 검색 타입 (t: 제목, c: 내용, w: 작성자)
     * @param keyword 검색 키워드
     * @param pageable 페이징 정보
     * @return 게시글과 댓글 수 정보
     */
    Page<BoardListReplyCountDTO> searchWithReplyCount(String[] types, String keyword, Pageable pageable);
    
    /**
     * 삭제된 게시글과 댓글 수를 함께 조회
     * @param types 검색 타입 (t: 제목, c: 내용, w: 작성자)
     * @param keyword 검색 키워드
     * @param pageable 페이징 정보
     * @return 게시글과 댓글 수 정보
     */
    Page<BoardListReplyCountDTO> searchDeletedWithReplyCount(String[] types, String keyword, Pageable pageable);


    //Board와 Reply를 레프트 조인 처리하고 쿼리를 실행해서 내용을 확인
//    Page<BoardListReplyCountDTO> searchWithAll(String[] types,
//                                               String keyword,
//                                               Pageable pageable);

    //QueryDsl 튜플처리 (기본적으로 삭제되지 않은 게시글만 검색)
    Page<BoardListAllDTO> searchWithAll(String[] types,
                                        String keyword,
                                        Long classId,
                                        Long categoryId,
                                        String boardType,
                                        Pageable pageable);
                                        
    //QueryDsl 튜플처리 - 삭제된 게시글 검색
    Page<BoardListAllDTO> searchDeletedWithAll(String[] types,
                                        String keyword,
                                        Long classId,
                                        Long categoryId,
                                        String boardType,
                                        Pageable pageable);


}
