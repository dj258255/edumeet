package com.edu.edumeet.board.infrastructure;

import com.edu.edumeet.base.BaseEntity;
import com.edu.edumeet.board.domain.BoardCategory;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "board_category")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "children")
@EntityListeners(AuditingEntityListener.class)
public class BoardCategoryJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String categoryName;

    private String description;

    @Column(nullable = false)
    private Long classId;

    @Column(nullable = false)
    private String createdBy;

    private Long parentId;

    @Column(nullable = false)
    private boolean isActive;

    @Column(nullable = false)
    private int sortOrder;

    @Builder.Default
    private int recommendThreshold = 10;  // 추천 게시글로 지정되는 기준값 (기본값 10)

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime regDate;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "parentId")
    @Builder.Default
    private List<BoardCategoryJpaEntity> children = new ArrayList<>();

    /**
     * 도메인 모델을 JPA 엔티티로 변환
     * @param boardCategory 도메인 모델
     * @return JPA 엔티티
     */
    public static BoardCategoryJpaEntity fromDomain(BoardCategory boardCategory) {
        BoardCategoryJpaEntity entity = BoardCategoryJpaEntity.builder()
                .id(boardCategory.getId())
                .categoryName(boardCategory.getCategoryName())
                .description(boardCategory.getDescription())
                .classId(boardCategory.getClassId())
                .createdBy(boardCategory.getCreatedBy())
                .parentId(boardCategory.getParentId())
                .isActive(boardCategory.isActive())
                .sortOrder(boardCategory.getSortOrder())
                .recommendThreshold(boardCategory.getRecommendThreshold())
                .regDate(boardCategory.getRegDate())
                .build();

        // 하위 카테고리가 있는 경우 변환
        if (boardCategory.hasChildren()) {
            List<BoardCategoryJpaEntity> childEntities = new ArrayList<>();
            for (BoardCategory child : boardCategory.getChildren()) {
                childEntities.add(fromDomain(child));
            }
            entity.children = childEntities;
        }

        return entity;
    }

    /**
     * JPA 엔티티를 도메인 모델로 변환
     * @return 도메인 모델
     */
    public BoardCategory toModel() {
        BoardCategory boardCategory = BoardCategory.builder()
                .id(this.id)
                .categoryName(this.categoryName)
                .description(this.description)
                .classId(this.classId)
                .createdBy(this.createdBy)
                .parentId(this.parentId)
                .isActive(this.isActive)
                .sortOrder(this.sortOrder)
                .recommendThreshold(this.recommendThreshold)
                .regDate(this.regDate)
                .build();

        // 하위 카테고리가 있는 경우 변환
        if (this.children != null && !this.children.isEmpty()) {
            List<BoardCategory> childModels = new ArrayList<>();
            for (BoardCategoryJpaEntity child : this.children) {
                childModels.add(child.toModel());
            }
            boardCategory = boardCategory.withChildren(childModels);
        }

        return boardCategory;
    }
}