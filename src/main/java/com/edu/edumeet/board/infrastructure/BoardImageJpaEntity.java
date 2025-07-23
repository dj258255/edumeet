package com.edu.edumeet.board.infrastructure;


import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "board_image")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = "boardJpaEntity")
public class BoardImageJpaEntity implements Comparable<BoardImageJpaEntity>{

    @Id
    private String uuid;

    private String filename;

    private int ord;

    @ManyToOne
    @JoinColumn(name = "board_id")
    private BoardJpaEntity boardJpaEntity;

    public int compareTo(BoardImageJpaEntity o) {
        return this.ord - o.ord;
    }

    //Board 객체를 나중에 지정할 수 있게 하는데 이것은 나중에 Board 엔티티 삭제 시에
    //BoardImage 객체의 참조도 변경하기 위해서 사용
    public void changeBoard(BoardJpaEntity board){
        this.boardJpaEntity = board;
    }
}
