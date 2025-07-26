package com.edu.edumeet.reply.presentation.dto;


import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
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

    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    private LocalDateTime regDate;

    @JsonIgnore
    private LocalDateTime modDate;
}
