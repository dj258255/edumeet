package com.edu.edumeet.board.presentation.dto;

import com.edu.edumeet.board.domain.BoardType;
import com.edu.edumeet.upload.presentation.dto.FileUploadDTO;
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

    private Long categoryId;

    private BoardType boardType;

    private long view;
    
    private long favorite;
    
    private long dislike;

    private List<FileUploadDTO> boardImages;
}
