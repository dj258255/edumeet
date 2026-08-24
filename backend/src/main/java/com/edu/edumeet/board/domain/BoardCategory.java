package com.edu.edumeet.board.domain;

import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Getter
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
@ToString
public class BoardCategory {
    private Long id;
    private String categoryName;
    private String description;
    private Long classId;
    private String createdBy;
    private Long parentId;         // 부모 카테고리 ID (인접 리스트 방식)
    private boolean isActive;
    private int sortOrder;
    private LocalDateTime regDate;
    
    @Builder.Default
    private int recommendThreshold = 10;  // 추천 게시글로 지정되는 기준값 (기본값 10)


    @Builder.Default
    private List<BoardCategory> children = new ArrayList<>();

    // 카테고리명 변경
    public BoardCategory changeName(String newName) {
        return BoardCategory.builder()
                .id(this.id)
                .categoryName(newName)
                .description(this.description)
                .classId(this.classId)
                .createdBy(this.createdBy)
                .parentId(this.parentId)
                .isActive(this.isActive)
                .sortOrder(this.sortOrder)
                .regDate(this.regDate)
                .children(this.children)
                .build();
    }

    // 카테고리 이동
    public BoardCategory moveTo(Long newParentId) {
        return BoardCategory.builder()
                .id(this.id)
                .categoryName(this.categoryName)
                .description(this.description)
                .classId(this.classId)
                .createdBy(this.createdBy)
                .parentId(newParentId)
                .isActive(this.isActive)
                .sortOrder(this.sortOrder)
                .regDate(this.regDate)
                .children(this.children)
                .build();
    }

    // 카테고리 비활성화
    public BoardCategory deactivate() {
        return BoardCategory.builder()
                .id(this.id)
                .categoryName(this.categoryName)
                .description(this.description)
                .classId(this.classId)
                .createdBy(this.createdBy)
                .parentId(this.parentId)
                .isActive(false)
                .sortOrder(this.sortOrder)
                .regDate(this.regDate)
                .children(this.children)
                .build();
    }

    // 하위 카테고리들 설정
    public BoardCategory withChildren(List<BoardCategory> children) {
        return BoardCategory.builder()
                .id(this.id)
                .categoryName(this.categoryName)
                .description(this.description)
                .classId(this.classId)
                .createdBy(this.createdBy)
                .parentId(this.parentId)
                .isActive(this.isActive)
                .sortOrder(this.sortOrder)
                .regDate(this.regDate)
                .children(children)
                .build();
    }

    // 루트 카테고리인지 확인
    public boolean isRootCategory() {
        return parentId == null;
    }

    // 하위 카테고리가 있는지 확인
    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }

    // 특정 깊이 확인 (최대 3단계)
    public int getDepth() {
        if (parentId == null) return 1;
        return 2; // 현재는 2단계만 지원
    }
    
    // 추천 게시글 기준값 변경
    public BoardCategory changeRecommendThreshold(int newThreshold) {
        if (newThreshold < 0) {
            throw new IllegalArgumentException("추천 게시글 기준값은 0 이상이어야 합니다.");
        }
        
        return BoardCategory.builder()
                .id(this.id)
                .categoryName(this.categoryName)
                .description(this.description)
                .classId(this.classId)
                .createdBy(this.createdBy)
                .parentId(this.parentId)
                .isActive(this.isActive)
                .sortOrder(this.sortOrder)
                .regDate(this.regDate)
                .recommendThreshold(newThreshold)
                .children(this.children)
                .build();
    }
}