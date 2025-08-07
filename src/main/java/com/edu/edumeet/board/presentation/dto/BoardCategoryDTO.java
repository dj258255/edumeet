package com.edu.edumeet.board.presentation.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 게시판 카테고리 DTO
 * 프레젠테이션 계층과 애플리케이션 계층 간의 데이터 전송에 사용
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BoardCategoryDTO {
    private Long id;
    private String categoryName;
    private String description;
    private Long classId;
    private String createdBy;
    private Long parentId;
    private boolean isActive;
    private int sortOrder;
    
    @Builder.Default
    private int recommendThreshold = 10;  // 추천 게시글로 지정되는 기준값 (기본값 10)
    
    private LocalDateTime regDate;
    
    @Builder.Default
    private List<BoardCategoryDTO> children = new ArrayList<>();
    
    /**
     * 루트 카테고리인지 확인
     * @return 루트 카테고리 여부
     */
    public boolean isRootCategory() {
        return parentId == null;
    }
    
    /**
     * 하위 카테고리가 있는지 확인
     * @return 하위 카테고리 존재 여부
     */
    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }
    
    /**
     * 카테고리 깊이 확인 (최대 3단계)
     * @return 카테고리 깊이
     */
    public int getDepth() {
        if (parentId == null) return 1;
        return 2; // 현재는 2단계만 지원
    }
}