package com.edu.edumeet.board.infrastructure;


import com.edu.edumeet.board.application.repository.BoardRepository;
import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.presentation.dto.BoardListReplyCountDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 게시판 도메인 레포지토리 구현체
 * DDD의 레포지토리 패턴을 구현
 */
@Repository
@RequiredArgsConstructor
public class BoardRepositoryImpl implements BoardRepository {

    private final BoardJpaRepository boardJpaRepository;

    /**
     * 게시글 저장
     * @param board 저장할 게시글 도메인 객체
     * @return 저장된 게시글의 ID
     */
    @Override
    public Long save(Board board) {
        // 도메인 모델을 JPA 엔티티로 변환
        BoardJpaEntity entity = BoardJpaEntity.fromDomain(board);
        // JPA 레포지토리를 통해 저장
        BoardJpaEntity savedEntity = boardJpaRepository.save(entity);
        // 저장된 엔티티의 ID 반환
        return savedEntity.getId();
    }

    /**
     * ID로 게시글 조회
     * @param id 게시글 ID
     * @return 조회된 게시글 (없으면 빈 Optional)
     */
    @Override
    public Optional<Board> findById(Long id) {
        // JPA 레포지토리를 통해 엔티티 조회
        Optional<BoardJpaEntity> entityOptional = boardJpaRepository.findById(id);
        // 엔티티를 도메인 모델로 변환하여 반환
        return entityOptional.map(BoardJpaEntity::toModel);
    }

    /**
     * 게시글 삭제
     * @param id 삭제할 게시글 ID
     */
    @Override
    public void deleteById(Long id) {
        // JPA 레포지토리를 통해 삭제
        boardJpaRepository.deleteById(id);
    }

    /**
     * 타입과 키워드로 게시글 검색
     * @param types 검색 타입 (t: 제목, c: 내용, w: 작성자)
     * @param keyword 검색 키워드
     * @param pageable 페이징 정보
     * @return 검색 결과
     */
    @Override
    public Page<Board> searchAll(String[] types, String keyword, Pageable pageable) {
        // BoardSearch 인터페이스의 searchAll 메소드 호출
        return boardJpaRepository.searchAll(types, keyword, pageable);
    }

    /**
     * 게시글과 댓글 수를 함께 조회
     * @param types 검색 타입 (t: 제목, c: 내용, w: 작성자)
     * @param keyword 검색 키워드
     * @param pageable 페이징 정보
     * @return 게시글과 댓글 수 정보
     */
    @Override
    public Page<BoardListReplyCountDTO> searchWithReplyCount(String[] types, String keyword, Pageable pageable) {
        // BoardSearch 인터페이스의 searchWithReplyCount 메소드 호출
        return boardJpaRepository.searchWithReplyCount(types, keyword, pageable);
    }
}
