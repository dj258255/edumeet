package com.edu.edumeet.board.application;

import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.presentation.dto.BoardListAllDTO;
import com.edu.edumeet.board.presentation.dto.BoardListReplyCountDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * 게시판 도메인 레포지토리 인터페이스
 * DDD의 레포지토리 패턴을 구현한 인터페이스
 */
public interface BoardRepository {

    //게시글 저장 , 게시글 ID 반환.
    Long save(Board board); // board ->long

    //id로 게시글 조회.
    //없으면 빈 Optional 반환.
    Optional<Board> findById(Long id); //long -> board

    //게시글 삭제. 삭제할 게시글 id
    void deleteById(Long id);

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

    Page<BoardListAllDTO> searchWithAll(String[] types, String keyword, Pageable pageable);
}
