package com.edu.edumeet.board.infrastructure;

import com.edu.edumeet.base.BaseEntity;
import com.edu.edumeet.board.domain.Board;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;


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


    //Board의 모든 상태변화에 Image들도 같이 변경되도록 구성.
    //Board 객체 자체에서 BoardImage들을 관리.
    @OneToMany(mappedBy = "boardJpaEntity",
            cascade = {CascadeType.ALL},
            fetch = FetchType.LAZY) //boardImage의 board변수이다.
    @Builder.Default
    private Set<BoardImageJpaEntity> imageSet = new HashSet<>();

    public void addImage(String uuid, String filename) {

        BoardImageJpaEntity boardImageJpaEntity = BoardImageJpaEntity.builder()
                .uuid(uuid)
                .filename(filename)
                .ord(imageSet.size())
                .boardJpaEntity(this)
                .build();
        imageSet.add(boardImageJpaEntity);
    }

    public void clearImages(){
        imageSet.forEach(boardImageJpaEntity -> boardImageJpaEntity.changeBoard(null));

        this.imageSet.clear();
    }



    public void change(String title, String content){
        this.title = title;
        this.content = content;
    }

    //보드 도메인을 모델로 변환
    public Board toModel(){
        return Board.builder()
                .id(this.id)
                .title(this.title)
                .content(this.content)
                .writer(this.writer)
                .regDate(this.getRegDate())  // BaseEntity에서 상속받은 등록일시
                .modDate(this.getModDate())  // BaseEntity에서 상속받은 수정일시
                .build();
    }

    //보드 도메인 모델로부터 JPA 엔티티를 생성하는 정적 팩토리 메소드
    public static BoardJpaEntity fromDomain(Board board){
        BoardJpaEntity entity = BoardJpaEntity.builder()
                .id(board.getId())
                .title(board.getTitle())
                .content(board.getContent())
                .writer(board.getWriter())
                .build();

        // 새로운 엔티티가 아닌 경우(ID가 있는 경우) 타임스탬프 정보 복사
        // 새로운 엔티티인 경우 JPA의 @CreatedDate, @LastModifiedDate가 자동으로 설정함
        if(board.getId() != null) {
            // 리플렉션이나 setter 메소드를 통해 설정할 수 있지만,
            // BaseEntity가 final 필드를 사용하므로 여기서는 생략
            // 실제로는 JPA가 엔티티를 로드할 때 이 값들을 설정함
        }

        return entity;
    }


    //보드 도메인 모델로 업데이트 하는 메소드
    public void updateDomain(Board board){
        // 기본 필드 업데이트
        this.title = board.getTitle();
        this.content = board.getContent();
        this.writer = board.getWriter();

        // 타임스탬프는 JPA의 @LastModifiedDate에 의해 자동으로 업데이트됨
    }
}
