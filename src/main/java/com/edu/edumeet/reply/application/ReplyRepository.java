package com.edu.edumeet.reply.application;


import com.edu.edumeet.board.presentation.dto.PageRequestDTO;
import com.edu.edumeet.board.presentation.dto.PageResponseDTO;
import com.edu.edumeet.reply.presentation.dto.ReplyDTO;

import java.util.List;

public interface ReplyRepository {

    /**
     * 댓글 등록
     * @param replyDTO 등록할 댓글 정보
     * @return 등록된 댓글의 ID
     */
    Long register(ReplyDTO replyDTO);

    /**
     * 댓글 조회
     * @param reply_id 조회할 댓글 ID
     * @return 조회된 댓글 정보
     */
    ReplyDTO read(Long reply_id);

    /**
     * 댓글 수정
     * @param replyDTO 수정할 댓글 정보
     */
    void modify(ReplyDTO replyDTO);

    /**
     * 댓글 삭제
     * @param reply_id 삭제할 댓글 ID
     */
    void remove(Long reply_id);

    /**
     * 특정 게시글의 댓글 목록 조회 (페이징)
     * @param board_id 게시글 ID
     * @param pageRequestDTO 페이지 요청 정보
     * @return 페이징된 댓글 목록
     */
    PageResponseDTO<ReplyDTO> getListOfBoard(Long board_id, PageRequestDTO pageRequestDTO);
    
    /**
     * 특정 게시글의 계층형 댓글 목록 조회 (페이징)
     * 최상위 댓글만 페이징하고, 각 최상위 댓글의 대댓글은 모두 포함
     * @param board_id 게시글 ID
     * @param pageRequestDTO 페이지 요청 정보
     * @return 페이징된 계층형 댓글 목록
     */
    PageResponseDTO<ReplyDTO> getHierarchicalListOfBoard(Long board_id, PageRequestDTO pageRequestDTO);
    
    /**
     * 특정 부모 댓글의 대댓글 목록 조회
     * @param parent_id 부모 댓글 ID
     * @return 대댓글 목록
     */
    List<ReplyDTO> getChildReplies(Long parent_id);
    
    /**
     * 특정 댓글이 대댓글을 가지고 있는지 확인
     * @param reply_id 댓글 ID
     * @return 대댓글 존재 여부
     */
    boolean hasChildReplies(Long reply_id);

    /**
     * 특정 게시글의 모든 댓글 삭제
     * @param boardId 게시글 ID
     */
    void deleteByBoardId(Long boardId);
}
