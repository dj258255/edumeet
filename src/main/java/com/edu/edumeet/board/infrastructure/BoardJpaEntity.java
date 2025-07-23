package com.edu.edumeet.board.infrastructure;

import com.edu.edumeet.base.BaseEntity;
import com.edu.edumeet.board.domain.Board;
import jakarta.persistence.*;
import lombok.*;


@Entity
@Table(name = "board")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
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
