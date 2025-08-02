package com.edu.edumeet.board.infrastructure;

import com.edu.edumeet.base.BaseEntity;
import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.domain.BoardImage;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

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
@ToString(exclude = "imageSet")
public class BoardJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title" , nullable = false , length = 200)
    private String title;

    @Column(name = "content" , columnDefinition = "TEXT")
    private String content;

    @Column(name = "writer" , nullable = false, length = 100)
    private String writer;

//    일단 임시
    @Column(name = "class_id" , nullable = false)
    private Long classId;

    //Board의 모든 상태변화에 Image들도 같이 변경되도록 구성.
    //Board 객체 자체에서 BoardImage들을 관리.
    //만일 하위 엔티티의 참조가 더 이상 없는 상태가 되면
    //OneToMay에 orphanRemoval 속성값을 true로 지정해 줘야만 실제 삭제가 이루어진다.
    @OneToMany(mappedBy = "boardJpaEntity",
            cascade = {CascadeType.ALL},
            fetch = FetchType.LAZY,
            orphanRemoval = true) //boardImage의 board변수이다.
    @Builder.Default
    @BatchSize(size = 20)
//    @Fetch(FetchMode.SUBSELECT)
    private Set<BoardImageJpaEntity> imageSet = new HashSet<>();
//
//    public void clearImages(){
//        imageSet.forEach(boardImageJpaEntity -> boardImageJpaEntity.changeBoard(null));
//
//        this.imageSet.clear();
//    }

    //보드 도메인을 모델로 변환
    public Board toModel(){
        Board board = Board.builder()
                .id(this.id)
                .title(this.title)
                .content(this.content)
                .writer(this.writer)
                .classId(this.classId)
                .regDate(this.getRegDate())
                .modDate(this.getModDate())
                .build();
        
        //이미지 정보 변환
        if(this.imageSet != null && !this.imageSet.isEmpty()){
            Set<BoardImage> domainImages = this.imageSet.stream()
                    .map(imgEntity -> BoardImage.builder()
                            .uuid(imgEntity.getUuid())
                            .fileName(imgEntity.getFilename())
                            .ord((imgEntity.getOrd()))
                            .build())
                    .collect(Collectors.toSet());
            board.setImages(domainImages);
        }
        
        return board;
    }

    //보드 도메인 모델로부터 JPA 엔티티를 생성하는 정적 팩토리 메소드
    public static BoardJpaEntity fromDomain(Board board){
        BoardJpaEntity entity = BoardJpaEntity.builder()
                .id(board.getId())
                .title(board.getTitle())
                .content(board.getContent())
                .writer(board.getWriter())
                .classId(board.getClassId())
                .build();

        //이미지 정보도 함께 변환
        if (board.getImages() != null && !board.getImages().isEmpty()) {
            Set<BoardImageJpaEntity> imageEntities = board.getImages().stream()
                    .map(domainImage -> BoardImageJpaEntity.builder()
                            .uuid(domainImage.getUuid())
                            .filename(domainImage.getFileName())
                            .ord(domainImage.getOrd())
                            .boardJpaEntity(entity)
                            .build())
                    .collect(Collectors.toSet());
            entity.imageSet = imageEntities;
        }

        return entity;
    }

    /**
     * 도메인 객체로부터 엔티티로 ㄱㄱ
     */
    /**
     * 도메인 객체로부터 엔티티로 업데이트
     */
    /**
     * 도메인 객체로부터 엔티티로 업데이트
     */
    public void updateFromDomain(Board board) {
        this.title = board.getTitle();
        this.content = board.getContent();
        this.classId = board.getClassId();

        // 기존 이미지들과의 연결 해제 (부모 참조 제거)
        this.imageSet.forEach(image -> image.changeBoard(null));  // 이미 있는 메서드 사용!
        this.imageSet.clear();

        // 새로운 이미지들 추가
        if(board.getImages() != null && !board.getImages().isEmpty()){
            Set<BoardImageJpaEntity> imageEntities = board.getImages().stream()
                    .map(domainImage -> BoardImageJpaEntity.builder()
                            .uuid(domainImage.getUuid())
                            .filename(domainImage.getFileName())
                            .ord(domainImage.getOrd())
                            .boardJpaEntity(this)
                            .build())
                    .collect(Collectors.toSet());

            // addAll을 사용하여 기존 컬렉션에 추가
            this.imageSet.addAll(imageEntities);
        }
    }
}
