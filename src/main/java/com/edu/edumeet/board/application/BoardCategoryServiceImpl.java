package com.edu.edumeet.board.application;

import com.edu.edumeet.board.domain.BoardCategory;
import com.edu.edumeet.board.presentation.BoardCategoryService;
import com.edu.edumeet.board.presentation.dto.BoardCategoryDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 게시판 카테고리 서비스 구현체
 * DDD의 애플리케이션 계층에서 도메인 모델을 사용하여 비즈니스 로직을 처리
 */
@Service
@Log4j2
@RequiredArgsConstructor
@Transactional
public class BoardCategoryServiceImpl implements BoardCategoryService {

    private final BoardCategoryRepository boardCategoryRepository;

    /**
     * 카테고리 등록
     * @param categoryDTO 등록할 카테고리 정보
     * @return 등록된 카테고리의 ID
     */
    @Override
    public Long register(BoardCategoryDTO categoryDTO) {
        BoardCategory category = dtoToDomain(categoryDTO);
        return boardCategoryRepository.save(category);
    }

    /**
     * 카테고리 조회
     * @param id 조회할 카테고리 ID
     * @return 조회된 카테고리 정보
     */
    @Override
    @Transactional(readOnly = true)
    public BoardCategoryDTO readOne(Long id) {
        Optional<BoardCategory> result = boardCategoryRepository.findById(id);
        BoardCategory category = result.orElseThrow(() -> 
            new IllegalArgumentException("카테고리를 찾을 수 없습니다: " + id));
        return domainToDto(category);
    }

    /**
     * 카테고리 수정
     * @param categoryDTO 수정할 카테고리 정보
     */
    @Override
    public void modify(BoardCategoryDTO categoryDTO) {
        Optional<BoardCategory> result = boardCategoryRepository.findById(categoryDTO.getId());
        BoardCategory category = result.orElseThrow(() -> 
            new IllegalArgumentException("카테고리를 찾을 수 없습니다: " + categoryDTO.getId()));
        
        // 카테고리 이름 변경
        if (!category.getCategoryName().equals(categoryDTO.getCategoryName())) {
            category = category.changeName(categoryDTO.getCategoryName());
        }
        
        // 추천 게시글 기준값 변경
        if (category.getRecommendThreshold() != categoryDTO.getRecommendThreshold()) {
            category = category.changeRecommendThreshold(categoryDTO.getRecommendThreshold());
        }
        
        // 활성화 상태 변경
        if (category.isActive() != categoryDTO.isActive()) {
            if (!categoryDTO.isActive()) {
                category = category.deactivate();
            } else {
                // 활성화 로직 (현재 도메인 모델에 없으므로 새로 생성)
                category = BoardCategory.builder()
                        .id(category.getId())
                        .categoryName(category.getCategoryName())
                        .description(categoryDTO.getDescription())
                        .classId(category.getClassId())
                        .createdBy(category.getCreatedBy())
                        .parentId(category.getParentId())
                        .isActive(true)
                        .sortOrder(category.getSortOrder())
                        .recommendThreshold(category.getRecommendThreshold())
                        .regDate(category.getRegDate())
                        .children(category.getChildren())
                        .build();
            }
        }
        
        // 저장
        boardCategoryRepository.save(category);
    }

    /**
     * 카테고리 삭제
     * @param id 삭제할 카테고리 ID
     */
    @Override
    public void remove(Long id) {
        // 하위 카테고리가 있는지 확인
        List<BoardCategory> subCategories = boardCategoryRepository.findByParentId(id);
        if (!subCategories.isEmpty()) {
            throw new IllegalStateException("하위 카테고리가 있는 카테고리는 삭제할 수 없습니다.");
        }
        
        boardCategoryRepository.deleteById(id);
    }

    /**
     * 클래스별 카테고리 목록 조회
     * @param classId 클래스 ID
     * @return 해당 클래스의 카테고리 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<BoardCategoryDTO> getListByClassId(Long classId) {
        List<BoardCategory> categories = boardCategoryRepository.findByClassId(classId);
        return categories.stream()
                .map(this::domainToDto)
                .collect(Collectors.toList());
    }

    /**
     * 루트 카테고리 목록 조회 (부모가 없는 카테고리)
     * @param classId 클래스 ID
     * @return 루트 카테고리 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<BoardCategoryDTO> getRootCategories(Long classId) {
        List<BoardCategory> rootCategories = boardCategoryRepository.findRootCategories(classId);
        return rootCategories.stream()
                .map(this::domainToDto)
                .collect(Collectors.toList());
    }

    /**
     * 하위 카테고리 목록 조회
     * @param parentId 부모 카테고리 ID
     * @return 하위 카테고리 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<BoardCategoryDTO> getSubCategories(Long parentId) {
        List<BoardCategory> subCategories = boardCategoryRepository.findByParentId(parentId);
        return subCategories.stream()
                .map(this::domainToDto)
                .collect(Collectors.toList());
    }

    /**
     * 카테고리 이동 (부모 카테고리 변경)
     * @param id 이동할 카테고리 ID
     * @param newParentId 새 부모 카테고리 ID
     */
    @Override
    public void moveCategory(Long id, Long newParentId) {
        // 자기 자신을 부모로 설정하는 것 방지
        if (id.equals(newParentId)) {
            throw new IllegalArgumentException("카테고리를 자기 자신의 하위로 이동할 수 없습니다.");
        }
        
        Optional<BoardCategory> result = boardCategoryRepository.findById(id);
        BoardCategory category = result.orElseThrow(() -> 
            new IllegalArgumentException("카테고리를 찾을 수 없습니다: " + id));
        
        // 새 부모 카테고리가 존재하는지 확인 (null이면 루트 카테고리로 이동)
        if (newParentId != null) {
            Optional<BoardCategory> parentResult = boardCategoryRepository.findById(newParentId);
            if (parentResult.isEmpty()) {
                throw new IllegalArgumentException("부모 카테고리를 찾을 수 없습니다: " + newParentId);
            }
            
            // 순환 참조 방지 (새 부모가 현재 카테고리의 하위 카테고리인 경우)
            List<BoardCategory> descendants = boardCategoryRepository.findByParentId(id);
            if (descendants.stream().anyMatch(desc -> desc.getId().equals(newParentId))) {
                throw new IllegalArgumentException("하위 카테고리를 부모 카테고리로 설정할 수 없습니다.");
            }
        }
        
        // 카테고리 이동
        BoardCategory updatedCategory = category.moveTo(newParentId);
        boardCategoryRepository.save(updatedCategory);
    }

    /**
     * 카테고리 활성화/비활성화
     * @param id 카테고리 ID
     * @param isActive 활성화 여부
     */
    @Override
    public void setActive(Long id, boolean isActive) {
        Optional<BoardCategory> result = boardCategoryRepository.findById(id);
        BoardCategory category = result.orElseThrow(() -> 
            new IllegalArgumentException("카테고리를 찾을 수 없습니다: " + id));
        
        if (category.isActive() != isActive) {
            if (!isActive) {
                // 비활성화
                category = category.deactivate();
            } else {
                // 활성화
                category = BoardCategory.builder()
                        .id(category.getId())
                        .categoryName(category.getCategoryName())
                        .description(category.getDescription())
                        .classId(category.getClassId())
                        .createdBy(category.getCreatedBy())
                        .parentId(category.getParentId())
                        .isActive(true)
                        .sortOrder(category.getSortOrder())
                        .recommendThreshold(category.getRecommendThreshold())
                        .regDate(category.getRegDate())
                        .children(category.getChildren())
                        .build();
            }
            
            boardCategoryRepository.save(category);
        }
    }

    /**
     * 카테고리 추천 게시글 기준값 변경
     * @param id 카테고리 ID
     * @param threshold 새 추천 게시글 기준값
     */
    @Override
    public void changeRecommendThreshold(Long id, int threshold) {
        Optional<BoardCategory> result = boardCategoryRepository.findById(id);
        BoardCategory category = result.orElseThrow(() -> 
            new IllegalArgumentException("카테고리를 찾을 수 없습니다: " + id));
        
        if (category.getRecommendThreshold() != threshold) {
            BoardCategory updatedCategory = category.changeRecommendThreshold(threshold);
            boardCategoryRepository.save(updatedCategory);
        }
    }
}