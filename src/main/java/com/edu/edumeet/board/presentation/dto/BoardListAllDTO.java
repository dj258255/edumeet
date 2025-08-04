package com.edu.edumeet.board.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BoardListAllDTO {

    private Long id;

    private String title;

    private String writer;

    private LocalDateTime regDate;

    private Long replyCount;

    private Long classId;
    
    private long view;
    
    private long favorite;

    private List<BoardImageDTO> boardImages;
}
