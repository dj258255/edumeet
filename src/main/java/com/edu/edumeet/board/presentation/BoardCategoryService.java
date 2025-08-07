package com.edu.edumeet.board.presentation;

import com.edu.edumeet.board.domain.BoardCategory;
import com.edu.edumeet.board.presentation.dto.BoardCategoryDTO;

import java.util.List;

/**
 * 게시판 카테고리 서비스 인터페이스
 * 애플리케이션 계층의 서비스로 도메인 모델을 사용하여 비즈니스 로직을 처리
 */
public interface BoardCategoryService {

    /**
     * BoardCategory 도메인 객체를 BoardCategoryDTO로 변환
     */
    default BoardCategoryDTO domainToDto(BoardCategory category) {
        return BoardCategoryDTO.builder()
                .id(category.getId())
                .categoryName(category.getCategoryName())
                .description(category.getDescription())
                .classId(category.getClassId())
                .createdBy(category.getCreatedBy())
                .parentId(category.getParentId())
                .isActive(category.isActive())
                .sortOrder(category.getSortOrder())
                .recommendThreshold(category.getRecommendThreshold())
                .regDate(category.getRegDate())
                .build();
    }

    /**
     * BoardCategoryDTO를 BoardCategory 도메인 객체로 변환
     */
    default BoardCategory dtoToDomain(BoardCategoryDTO dto) {
        return BoardCategory.builder()
                .id(dto.getId())
                .categoryName(dto.getCategoryName())
                .description(dto.getDescription())
                .classId(dto.getClassId())
                .createdBy(dto.getCreatedBy())
                .parentId(dto.getParentId())
                .isActive(dto.isActive())
                .sortOrder(dto.getSortOrder())
                .recommendThreshold(dto.getRecommendThreshold())
                .regDate(dto.getRegDate())
                .build();
    }

    /**
     * 카테고리 등록
     * @param categoryDTO 등록할 카테고리 정보
     * @return 등록된 카테고리의 ID
     */
    Long register(BoardCategoryDTO categoryDTO);

    /**
     * 카테고리 조회
     * @param id 조회할 카테고리 ID
     * @return 조회된 카테고리 정보
     */
    BoardCategoryDTO readOne(Long id);

    /**
     * 카테고리 수정
     * @param categoryDTO 수정할 카테고리 정보
     */
    void modify(BoardCategoryDTO categoryDTO);

    /**
     * 카테고리 삭제
     * @param id 삭제할 카테고리 ID
     */
    void remove(Long id);

    /**
     * 클래스별 카테고리 목록 조회
     * @param classId 클래스 ID
     * @return 해당 클래스의 카테고리 목록
     */
    List<BoardCategoryDTO> getListByClassId(Long classId);

    /**
     * 루트 카테고리 목록 조회 (부모가 없는 카테고리)
     * @param classId 클래스 ID
     * @return 루트 카테고리 목록
     */
    List<BoardCategoryDTO> getRootCategories(Long classId);

    /**
     * 하위 카테고리 목록 조회
     * @param parentId 부모 카테고리 ID
     * @return 하위 카테고리 목록
     */
    List<BoardCategoryDTO> getSubCategories(Long parentId);

    /**
     * 카테고리 이동 (부모 카테고리 변경)
     * @param id 이동할 카테고리 ID
     * @param newParentId 새 부모 카테고리 ID
     */
    void moveCategory(Long id, Long newParentId);

    /**
     * 카테고리 활성화/비활성화
     * @param id 카테고리 ID
     * @param isActive 활성화 여부
     */
    void setActive(Long id, boolean isActive);

    /**
     * 카테고리 추천 게시글 기준값 변경
     * @param id 카테고리 ID
     * @param threshold 새 추천 게시글 기준값
     */
    void changeRecommendThreshold(Long id, int threshold);
}