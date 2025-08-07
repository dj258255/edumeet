package com.edu.edumeet.board.application;

import com.edu.edumeet.board.domain.BoardCategory;

import java.util.List;
import java.util.Optional;

/**
 * 게시판 카테고리 도메인 레포지토리 인터페이스
 * DDD의 레포지토리 패턴을 구현한 인터페이스
 */
public interface BoardCategoryRepository {

    /**
     * 카테고리 저장
     * @param boardCategory 저장할 카테고리 도메인 객체
     * @return 저장된 카테고리의 ID
     */
    Long save(BoardCategory boardCategory);

    /**
     * ID로 카테고리 조회
     * @param id 조회할 카테고리 ID
     * @return 조회된 카테고리, 없으면 빈 Optional 반환
     */
    Optional<BoardCategory> findById(Long id);

    /**
     * 클래스 ID로 카테고리 목록 조회
     * @param classId 클래스 ID
     * @return 해당 클래스의 카테고리 목록
     */
    List<BoardCategory> findByClassId(Long classId);

    /**
     * 부모 카테고리 ID로 하위 카테고리 목록 조회
     * @param parentId 부모 카테고리 ID
     * @return 하위 카테고리 목록
     */
    List<BoardCategory> findByParentId(Long parentId);

    /**
     * 루트 카테고리 목록 조회 (부모가 없는 카테고리)
     * @param classId 클래스 ID
     * @return 루트 카테고리 목록
     */
    List<BoardCategory> findRootCategories(Long classId);

    /**
     * 카테고리 삭제
     * @param id 삭제할 카테고리 ID
     */
    void deleteById(Long id);
}