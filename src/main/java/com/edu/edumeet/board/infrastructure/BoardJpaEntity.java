package com.edu.edumeet.board.infrastructure;

import com.edu.edumeet.attachment.domain.Attachment;
import com.edu.edumeet.base.BaseEntity;
import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.domain.BoardType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;


@Entity
@Table(name = "board")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class BoardJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title" , nullable = false , length = 50)
    private String title;

    @Column(name = "content" , columnDefinition = "TEXT")
    private String content;

    @Column(name = "writer" , nullable = false, length = 100)
    private String writer;

    @Column(name = "class_id" , nullable = false)
    private Long classId;
    
    @Column(name = "category_id")
    private Long categoryId;
    
    @Column(name = "board_type", columnDefinition = "VARCHAR(20) DEFAULT 'NORMAL'")
    @Enumerated(EnumType.STRING)
    private BoardType boardType;
    
    @Column(name = "view", columnDefinition = "BIGINT DEFAULT 0")
    private long view;
    
    @Column(name = "favorite", columnDefinition = "BIGINT DEFAULT 0")
    private long favorite;
    
    @Column(name = "dislike", columnDefinition = "BIGINT DEFAULT 0")
    private long dislike;

    // FileUploadJpaEntity와의 직접적인 관계 제거
    // 대신 BoardFileUploadJpaEntity를 통해 연관관계 매핑

    // 이미지 관련 필드 추가 - BoardFileUploadJpaEntity와의 연관관계 매핑
    @OneToMany(mappedBy = "boardJpaEntity",
            cascade = {CascadeType.ALL},
            fetch = FetchType.LAZY,
            orphanRemoval = true)
    @Builder.Default
    @BatchSize(size = 20)
    private Set<BoardFileUploadJpaEntity> imageSet = new HashSet<>();

    // 보드 도메인을 모델로 변환
    public Board toModel() {
        Board board = Board.builder()
                .id(this.id)
                .title(this.title)
                .content(this.content)
                .writer(this.writer)
                .classId(this.classId)
                .categoryId(this.categoryId)
                .boardType(this.boardType)
                .regDate(this.getRegDate())
                .modDate(this.getModDate())
                .deletedAt(this.getDeletedAt())
                .view(this.view)
                .favorite(this.favorite)
                .dislike(this.dislike)
                .build();
        
        // 이미지 정보 변환 - imageSet에서 직접 가져옴
        if (this.imageSet != null && !this.imageSet.isEmpty()) {
            Set<Attachment> domainImages = this.imageSet.stream()
                    .map(fileEntity -> Attachment.builder()
                            .id(fileEntity.getId())
                            .uuid(fileEntity.getUuid())
                            .fileName(fileEntity.getFileName())
                            .ord(fileEntity.getOrd())
                            .img(fileEntity.isImg())
                            .domain("board")
                            .referenceId(fileEntity.getReferenceId())
                            .fileSize(fileEntity.getFileSize())
                            .contentType(fileEntity.getContentType())
                            .uploadedAt(fileEntity.getRegDate())
                            .uploadedBy(fileEntity.getUploadedBy())
                            .build())
                    .collect(Collectors.toSet());
            board.setImages(domainImages);
        }
        
        return board;
    }
    

    // 보드 도메인 모델로부터 JPA 엔티티를 생성하는 정적 팩토리 메소드
    public static BoardJpaEntity fromDomain(Board board) {
        BoardJpaEntity entity = BoardJpaEntity.builder()
                .id(board.getId())
                .title(board.getTitle())
                .content(board.getContent())
                .writer(board.getWriter())
                .classId(board.getClassId())
                .categoryId(board.getCategoryId())
                .boardType(board.getBoardType())
                .view(board.getView())
                .favorite(board.getFavorite())
                .dislike(board.getDislike())
                .build();

        if (board.getDeletedAt() != null) {
            entity.markDeleted();
        }

        // 이미지 정보는 별도로 처리해야 함
        // 이미지 정보는 BoardRepositoryImpl에서 FileUploadJpaEntity를 생성하고
        // boardJpaEntity 필드를 설정하여 연결해야 함
        
        return entity;
    }

    /**
     * 도메인 객체로부터 엔티티로 업데이트
     */
    public void updateFromDomain(Board board) {
        this.title = board.getTitle();
        this.content = board.getContent();
        this.classId = board.getClassId();
        this.categoryId = board.getCategoryId();
        this.boardType = board.getBoardType();
        this.view = board.getView();
        this.favorite = board.getFavorite();
        this.dislike = board.getDislike();
        
        // 이미지 정보는 FileUploadJpaEntity에서 별도로 처리
    }
}