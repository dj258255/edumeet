package com.edu.edumeet.reply.presentation.dto;


import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplyDTO {

    private Long id;

    @NotNull
    private Long boardId;

    @NotEmpty
    private String replyText;

    @NotEmpty
    private String replayer;

    private LocalDateTime regDate, modDate;
}
