package com.edu.edumeet.board.repository;

import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.dto.BoardListAllDTO;
import com.edu.edumeet.board.dto.BoardListReplyCountDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * 게시판 도메인 레포지토리 인터페이스
 * DDD의 레포지토리 패턴을 구현한 인터페이스
 */
public interface BoardRepository {
    Long save(Board board); // null 반환 가능
    Optional<Board> findById(Long id);
    void deleteById(Long id);
    boolean restoreById(Long id); // boolean 반환으로 변경
    Optional<Board> findByIdIncludeDeleted(Long id);
    Page<Board> searchAll(String[] types, String keyword, Pageable pageable);
    Page<BoardListReplyCountDTO> searchWithReplyCount(String[] types, String keyword, Pageable pageable);
    Page<BoardListAllDTO> searchWithAll(String[] types, String keyword, Long classId, Long categoryId, String boardType, Pageable pageable);
}