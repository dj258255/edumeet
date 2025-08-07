package com.edu.edumeet.reply.infrastructure;

import com.edu.edumeet.board.presentation.dto.PageRequestDTO;
import com.edu.edumeet.board.presentation.dto.PageResponseDTO;
import com.edu.edumeet.reply.application.ReplyRepository;
import com.edu.edumeet.reply.domain.Reply;
import com.edu.edumeet.reply.presentation.dto.ReplyDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.Collections;
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
        // 서비스 계층에서 이미 유효성 검사를 수행했으므로 여기서는 저장만 수행
        Reply reply = dtoToDomain(replyDTO);
        ReplyJpaEntity entity = ReplyJpaEntity.fromDomain(reply);
        ReplyJpaEntity savedEntity = replyJpaRepository.save(entity);
        return savedEntity.getId();
    }


    /**
     * 댓글 조회 (삭제된 댓글 제외)
     * @param reply_id 조회할 댓글 ID
     * @return 조회된 댓글 정보
     */
    @Override
    public ReplyDTO read(Long reply_id) {
        // 삭제되지 않은 댓글만 조회
        Optional<ReplyJpaEntity> result = replyJpaRepository.findByIdNotDeleted(reply_id);
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
        
        ReplyJpaEntity existingEntity = result.get();
        
        // 기존 엔티티의 모든 관계와 메타데이터를 보존하면서 내용만 수정
        existingEntity.setReplyText(replyDTO.getReplyText());
        existingEntity.setReplayer(replyDTO.getReplayer());
        // modDate는 BaseEntity의 @PreUpdate에 의해 자동 설정됨
        
        replyJpaRepository.save(existingEntity);
    }

    /**
     * 댓글 삭제 (논리적 삭제)
     * 댓글 삭제 시 하위 댓글도 함께 삭제
     * @param reply_id 삭제할 댓글 ID
     */
    @Override
    public void remove(Long reply_id) {
        Optional<ReplyJpaEntity> result = replyJpaRepository.findById(reply_id);
        if (result.isPresent()) {
            ReplyJpaEntity entity = result.get();
            
            // 1. 해당 댓글 논리적 삭제
            entity.markDeleted();
            replyJpaRepository.save(entity);
            
            // 2. 하위 댓글이 있는 경우 모두 논리적 삭제
            List<ReplyJpaEntity> childReplies = replyJpaRepository.findByParentReplyId(reply_id);
            if (!childReplies.isEmpty()) {
                childReplies.forEach(childReply -> {
                    childReply.markDeleted();
                    replyJpaRepository.save(childReply);
                });
            }
        }
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
                .map(this::entityToDto)
                .collect(Collectors.toList());
        
        return new PageResponseDTO<>(pageRequestDTO, dtoList, (int)result.getTotalElements());
    }
    
    /**
     * 특정 게시글의 계층형 댓글 목록 조회 (페이징)
     * 최상위 댓글만 페이징하고, 각 최상위 댓글의 대댓글은 모두 포함
     * @param board_id 게시글 ID
     * @param pageRequestDTO 페이지 요청 정보
     * @return 페이징된 계층형 댓글 목록
     */
    @Override
    public PageResponseDTO<ReplyDTO> getHierarchicalListOfBoard(Long board_id, PageRequestDTO pageRequestDTO) {
        // 최상위 댓글만 페이징 조회
        Pageable pageable = PageRequest.of(
                pageRequestDTO.getPage() - 1,
                pageRequestDTO.getSize(),
                Sort.by("id").ascending());
        
        Page<ReplyJpaEntity> result = replyJpaRepository.listOfBoardRootReplies(board_id, pageable);

        //댓글이 없는 경우에도 빈 PageResponseDTO를 반환
        if(result.getTotalElements() == 0){
            return PageResponseDTO.<ReplyDTO>of()
                    .page(pageRequestDTO.getPage())
                    .size(pageRequestDTO.getSize())
                    .dtoList(Collections.emptyList()) //빈 리스트 반환
                    .total(0)
                    .build();
        }

        // 최상위 댓글을 DTO로 변환
        List<ReplyDTO> rootReplies = result.getContent().stream()
                .map(this::entityToDto)
                .collect(Collectors.toList());
        
        // 각 최상위 댓글에 대댓글 추가
        rootReplies.forEach(rootReply -> {
            List<ReplyDTO> childReplies = getChildReplies(rootReply.getId());
            rootReply.setChildren(childReplies);
        });

        
        return new PageResponseDTO<>(pageRequestDTO, rootReplies, (int)result.getTotalElements());
    }
    
    /**
     * 특정 부모 댓글의 대댓글 목록 조회
     * @param parent_id 부모 댓글 ID
     * @return 대댓글 목록
     */
    @Override
    public List<ReplyDTO> getChildReplies(Long parent_id) {
        List<ReplyJpaEntity> childEntities = replyJpaRepository.findByParentReplyId(parent_id);
        return childEntities.stream()
                .map(this::entityToDto)
                .collect(Collectors.toList());
    }
    
    /**
     * 특정 댓글이 대댓글을 가지고 있는지 확인
     * @param reply_id 댓글 ID
     * @return 대댓글 존재 여부
     */
    @Override
    public boolean hasChildReplies(Long reply_id) {
        return replyJpaRepository.hasChildReplies(reply_id);
    }


    /**
     * 게시글에 속한 모든 댓글 삭제 (논리적 삭제)
     * @param boardId 게시글 ID
     */
    @Override
    public void deleteByBoardId(Long boardId){
        // 게시글에 속한 모든 댓글 조회
        List<ReplyJpaEntity> replies = replyJpaRepository.findByBoard_Id(boardId);
        
        // 각 댓글을 논리적으로 삭제
        for (ReplyJpaEntity reply : replies) {
            reply.markDeleted(); // BaseEntity의 markDeleted 메서드 호출
            replyJpaRepository.save(reply);
        }
    }



    /**
     * ReplyDTO를 Reply 도메인 객체로 변환
     * @param dto ReplyDTO 객체
     * @return Reply 도메인 객체
     */
    private Reply dtoToDomain(ReplyDTO dto) {
        Reply.ReplyBuilder builder = Reply.builder()
                .id(dto.getId())
                .boardId(dto.getBoardId())
                .replyText(dto.getReplyText())
                .replayer(dto.getReplayer())
                .regDate(dto.getRegDate())
                .modDate(dto.getModDate())
                .depth(dto.getDepth());
                
        // 부모 댓글 ID가 있는 경우 설정
        if (dto.getParentReplyId() != null) {
            builder.parentReplyId(dto.getParentReplyId());
        }
        
        return builder.build();
    }
    
    /**
     * Reply 도메인 객체를 ReplyDTO로 변환
     * @param domain Reply 도메인 객체
     * @return ReplyDTO 객체
     */
    private ReplyDTO domainToDto(Reply domain) {
        ReplyDTO.ReplyDTOBuilder builder = ReplyDTO.builder()
                .id(domain.getId())
                .boardId(domain.getBoardId())
                .replyText(domain.getReplyText())
                .replayer(domain.getReplayer())
                .regDate(domain.getRegDate())
                .modDate(domain.getModDate())
                .depth(domain.getDepth());
                
        // 부모 댓글 ID가 있는 경우 설정
        if (domain.getParentReplyId() != null) {
            builder.parentReplyId(domain.getParentReplyId());
        }
        
        return builder.build();
    }
    
    /**
     * ReplyJpaEntity를 ReplyDTO로 변환
     * @param entity ReplyJpaEntity 객체
     * @return ReplyDTO 객체
     */
    private ReplyDTO entityToDto(ReplyJpaEntity entity) {
        ReplyDTO.ReplyDTOBuilder builder = ReplyDTO.builder()
                .id(entity.getId())
                .boardId(entity.getBoard().getId())
                .replyText(entity.getReplyText())
                .replayer(entity.getReplayer())
                .regDate(entity.getRegDate())
                .modDate(entity.getModDate())
                .depth(entity.getDepth());
                
        // 부모 댓글이 있는 경우 부모 댓글 ID 설정
        if (entity.getParentReply() != null) {
            builder.parentReplyId(entity.getParentReply().getId());
        }
        
        return builder.build();
    }


}