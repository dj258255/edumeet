package com.edu.edumeet.reply.application;


import com.edu.edumeet.board.presentation.dto.PageRequestDTO;
import com.edu.edumeet.board.presentation.dto.PageResponseDTO;
import com.edu.edumeet.reply.presentation.ReplyService;
import com.edu.edumeet.reply.presentation.dto.ReplyDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Log4j2
public class ReplyServiceImpl implements ReplyService {

    private final ReplyRepository replyRepository;

    /**
     * 댓글 등록
     * @param replyDTO 등록할 댓글 정보
     * @return 등록된 댓글의 ID
     */
    @Override
    public Long register(ReplyDTO replyDTO) {
        log.info("댓글 등록: {}", replyDTO);
        return replyRepository.register(replyDTO);
    }

    /**
     * 댓글 조회
     * @param reply_id 조회할 댓글 ID
     * @return 조회된 댓글 정보
     */
    @Override
    public ReplyDTO read(Long reply_id) {
        log.info("댓글 조회: {}", reply_id);
        return replyRepository.read(reply_id);
    }

    /**
     * 댓글 수정
     * @param replyDTO 수정할 댓글 정보
     */
    @Override
    public void modify(ReplyDTO replyDTO) {
        log.info("댓글 수정: {}", replyDTO);
        replyRepository.modify(replyDTO);
    }

    /**
     * 댓글 삭제
     * @param reply_id 삭제할 댓글 ID
     */
    @Override
    public void remove(Long reply_id) {
        log.info("댓글 삭제: {}", reply_id);
        replyRepository.remove(reply_id);
    }

    /**
     * 특정 게시글의 댓글 목록 조회
     * @param board_id 게시글 ID
     * @param pageRequestDTO 페이지 요청 정보
     * @return 페이징된 댓글 목록
     */
    @Override
    public PageResponseDTO<ReplyDTO> getListOfBoard(Long board_id, PageRequestDTO pageRequestDTO) {
        log.info("게시글 {}의 댓글 목록 조회, 페이지: {}", board_id, pageRequestDTO);
        return replyRepository.getListOfBoard(board_id, pageRequestDTO);
    }
}