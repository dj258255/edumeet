package com.edu.edumeet.board.repository;

import com.edu.edumeet.board.domain.BoardCategoryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 게시판 카테고리 JPA 리포지토리 인터페이스
 */
public interface BoardCategoryJpaRepository extends JpaRepository<BoardCategoryJpaEntity, Long> {

    /**
     * 클래스 ID로 카테고리 목록 조회
     * @param classId 클래스 ID
     * @return 해당 클래스의 카테고리 목록
     */
    List<BoardCategoryJpaEntity> findByClassId(Long classId);

    /**
     * 부모 카테고리 ID로 하위 카테고리 목록 조회
     * @param parentId 부모 카테고리 ID
     * @return 하위 카테고리 목록
     */
    List<BoardCategoryJpaEntity> findByParentId(Long parentId);

    /**
     * 루트 카테고리 목록 조회 (부모가 없는 카테고리)
     * @param classId 클래스 ID
     * @return 루트 카테고리 목록
     */
    List<BoardCategoryJpaEntity> findByClassIdAndParentIdIsNull(Long classId);
    
    /**
     * 활성화된 카테고리 목록 조회
     * @param classId 클래스 ID
     * @return 활성화된 카테고리 목록
     */
    List<BoardCategoryJpaEntity> findByClassIdAndIsActiveTrue(Long classId);
    
    /**
     * 카테고리 이름으로 검색
     * @param classId 클래스 ID
     * @param categoryName 카테고리 이름 (부분 일치)
     * @return 검색된 카테고리 목록
     */
    List<BoardCategoryJpaEntity> findByClassIdAndCategoryNameContaining(Long classId, String categoryName);
    
    /**
     * 특정 클래스의 카테고리 수 조회
     * @param classId 클래스 ID
     * @return 카테고리 수
     */
    long countByClassId(Long classId);
    
    /**
     * 특정 부모 카테고리 아래의 하위 카테고리 수 조회
     * @param parentId 부모 카테고리 ID
     * @return 하위 카테고리 수
     */
    long countByParentId(Long parentId);
    
    /**
     * 특정 클래스의 모든 카테고리 삭제
     * @param classId 클래스 ID
     */
    void deleteByClassId(Long classId);
    
    /**
     * 특정 부모 카테고리 아래의 모든 하위 카테고리 조회 (정렬 순서 적용)
     * @param parentId 부모 카테고리 ID
     * @return 정렬된 하위 카테고리 목록
     */
    List<BoardCategoryJpaEntity> findByParentIdOrderBySortOrderAsc(Long parentId);
    
    /**
     * 특정 클래스의 모든 루트 카테고리 조회 (정렬 순서 적용)
     * @param classId 클래스 ID
     * @return 정렬된 루트 카테고리 목록
     */
    List<BoardCategoryJpaEntity> findByClassIdAndParentIdIsNullOrderBySortOrderAsc(Long classId);
    
    /**
     * 특정 클래스의 특정 카테고리 이름이 이미 존재하는지 확인
     * @param classId 클래스 ID
     * @param categoryName 카테고리 이름
     * @return 존재 여부
     */
    boolean existsByClassIdAndCategoryName(Long classId, String categoryName);
}