package com.edu.edumeet.reply.presentation;


import com.edu.edumeet.board.presentation.dto.PageRequestDTO;
import com.edu.edumeet.board.presentation.dto.PageResponseDTO;
import com.edu.edumeet.reply.presentation.dto.ReplyDTO;

public interface ReplyService {

    Long register(ReplyDTO replyDTO);

    ReplyDTO read(Long reply_id);

    void modify(ReplyDTO replyDTO);

    void remove(Long reply_id);

    PageResponseDTO<ReplyDTO> getListOfBoard(Long board_id, PageRequestDTO pageRequestDTO);

}