package com.edu.edumeet.reply.infrastructure;

import com.edu.edumeet.board.presentation.dto.PageRequestDTO;
import com.edu.edumeet.board.presentation.dto.PageResponseDTO;
import com.edu.edumeet.reply.application.repository.ReplyRepository;
import com.edu.edumeet.reply.domain.Reply;
import com.edu.edumeet.reply.presentation.dto.ReplyDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ReplyRepositoryImpl implements ReplyRepository {

    private final ReplyJpaRepository replyJpaRepository;

    /**
     * 댓글 등록
     * @param replyDTO 등록할 댓글 정보
     * @return 등록된 댓글의 ID
     */
    @Override
    public Long register(ReplyDTO replyDTO) {
        Reply reply = dtoToDomain(replyDTO);
        ReplyJpaEntity entity = ReplyJpaEntity.fromDomain(reply);
        ReplyJpaEntity savedEntity = replyJpaRepository.save(entity);
        return savedEntity.getId();
    }

    /**
     * 댓글 조회
     * @param reply_id 조회할 댓글 ID
     * @return 조회된 댓글 정보
     */
    @Override
    public ReplyDTO read(Long reply_id) {
        Optional<ReplyJpaEntity> result = replyJpaRepository.findById(reply_id);
        if (result.isEmpty()) {
            return null;
        }
        
        ReplyJpaEntity entity = result.get();
        return entityToDto(entity);
    }

    /**
     * 댓글 수정
     * @param replyDTO 수정할 댓글 정보
     */
    @Override
    public void modify(ReplyDTO replyDTO) {
        Optional<ReplyJpaEntity> result = replyJpaRepository.findById(replyDTO.getId());
        if (result.isEmpty()) {
            return;
        }
        
        // 댓글 내용만 수정 가능
        Reply reply = dtoToDomain(replyDTO);
        ReplyJpaEntity entity = ReplyJpaEntity.fromDomain(reply);
        // 기존 엔티티의 board 관계를 유지
        entity = ReplyJpaEntity.builder()
                .id(entity.getId())
                .replyText(entity.getReplyText())
                .replayer(entity.getReplayer())
                .board(result.get().getBoard())
                .build();
        
        replyJpaRepository.save(entity);
    }

    /**
     * 댓글 삭제
     * @param reply_id 삭제할 댓글 ID
     */
    @Override
    public void remove(Long reply_id) {
        replyJpaRepository.deleteById(reply_id);
    }

    /**
     * 특정 게시글의 댓글 목록 조회
     * @param board_id 게시글 ID
     * @param pageRequestDTO 페이지 요청 정보
     * @return 페이징된 댓글 목록
     */
    @Override
    public PageResponseDTO<ReplyDTO> getListOfBoard(Long board_id, PageRequestDTO pageRequestDTO) {
        Pageable pageable = PageRequest.of(
                pageRequestDTO.getPage() - 1,
                pageRequestDTO.getSize(),
                Sort.by("id").ascending());
        
        Page<ReplyJpaEntity> result = replyJpaRepository.listOfBoard(board_id, pageable);
        
        List<ReplyDTO> dtoList = result.getContent().stream()
                .map(entity -> entityToDto(entity))
                .collect(Collectors.toList());
        
        return new PageResponseDTO<>(pageRequestDTO, dtoList, (int)result.getTotalElements());
    }
    
    /**
     * ReplyDTO를 Reply 도메인 객체로 변환
     * @param dto ReplyDTO 객체
     * @return Reply 도메인 객체
     */
    private Reply dtoToDomain(ReplyDTO dto) {
        return Reply.builder()
                .id(dto.getId())
                .boardId(dto.getBoardId())
                .replyText(dto.getReplyText())
                .replayer(dto.getReplayer())
                .build();
    }
    
    /**
     * Reply 도메인 객체를 ReplyDTO로 변환
     * @param domain Reply 도메인 객체
     * @return ReplyDTO 객체
     */
    private ReplyDTO domainToDto(Reply domain) {
        return ReplyDTO.builder()
                .id(domain.getId())
                .boardId(domain.getBoardId())
                .replyText(domain.getReplyText())
                .replayer(domain.getReplayer())
                .build();
    }
    
    /**
     * ReplyJpaEntity를 ReplyDTO로 변환
     * @param entity ReplyJpaEntity 객체
     * @return ReplyDTO 객체
     */
    private ReplyDTO entityToDto(ReplyJpaEntity entity) {
        return ReplyDTO.builder()
                .id(entity.getId())
                .boardId(entity.getBoard().getId())
                .replyText(entity.getReplyText())
                .replayer(entity.getReplayer())
                .regDate(entity.getRegDate())
                .modDate(entity.getModDate())
                .build();
    }
}