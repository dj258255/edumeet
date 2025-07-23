package com.edu.edumeet.board.domain;

import lombok.*;

@Getter
@Setter
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
@ToString
public class BoardImage implements Comparable<BoardImage>{

    private String uuid;

    private String fileName;

    private int ord;

    private Board board;

    @Override
    public int compareTo(BoardImage o) {
        return this.ord - o.ord;
    }

}
