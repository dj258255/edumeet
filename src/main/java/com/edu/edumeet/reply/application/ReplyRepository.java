package com.edu.edumeet.reply.application;


import com.edu.edumeet.board.presentation.dto.PageRequestDTO;
import com.edu.edumeet.board.presentation.dto.PageResponseDTO;
import com.edu.edumeet.reply.presentation.dto.ReplyDTO;

public interface ReplyRepository {


    public Long register(ReplyDTO replyDTO);

    public ReplyDTO read(Long reply_id);

    public void modify(ReplyDTO replyDTO);

    public void remove(Long reply_id);

    public PageResponseDTO<ReplyDTO> getListOfBoard(Long board_id, PageRequestDTO pageRequestDTO);

    //특정 게시글의 모든 댓글 삭제
    public void deleteByBoardId(Long boardId);


}
