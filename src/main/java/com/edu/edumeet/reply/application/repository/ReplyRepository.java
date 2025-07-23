package com.edu.edumeet.reply.application.repository;


import com.edu.edumeet.board.presentation.dto.PageRequestDTO;
import com.edu.edumeet.board.presentation.dto.PageResponseDTO;
import com.edu.edumeet.reply.presentation.dto.ReplyDTO;

public interface ReplyRepository {


    public Long register(ReplyDTO replyDTO);

    public ReplyDTO read(Long rno);

    public void modify(ReplyDTO replyDTO);

    public void remove(Long rno);

    public PageResponseDTO<ReplyDTO> getListOfBoard(Long bno, PageRequestDTO pageRequestDTO);
}
