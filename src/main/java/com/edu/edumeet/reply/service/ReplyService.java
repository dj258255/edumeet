package com.edu.edumeet.reply.service;

import com.edu.edumeet.board.dto.PageRequestDTO;
import com.edu.edumeet.board.dto.PageResponseDTO;
import com.edu.edumeet.reply.domain.Reply;
import com.edu.edumeet.reply.dto.ReplyDTO;
import com.edu.edumeet.reply.repository.ReplyRepository;
import com.edu.edumeet.board.domain.BoardJpaEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 댓글 서비스.
 *
 * 이전에는 검증만 하는 ReplyService(166줄)과
 * 실제 영속화·비즈니스 로직을 담은 ReplyRepositoryImpl(280줄)로 나뉘어 있었다.
 * 리포지토리가 DTO 를 반환하고 자식 댓글 연쇄 삭제 같은 규칙까지 갖고 있어
 * 계층 책임이 뒤집힌 상태였다. 하나로 합쳤다. (#15)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Log4j2
public class ReplyService {

    private static final int MAX_REPLY_LENGTH = 255;
    /** 최대 1계층까지만 허용한다. 대댓글에는 댓글을 달 수 없다. */
    private static final int MAX_DEPTH = 1;

    private final ReplyRepository replyRepository;

    @Transactional
    public Long register(ReplyDTO replyDTO) {
        log.info("댓글 등록: {}", replyDTO);
        validateReplyText(replyDTO.getReplyText());

        if (replyDTO.getParentReplyId() != null) {
            ReplyDTO parentReply = read(replyDTO.getParentReplyId());
            if (parentReply == null) {
                throw new IllegalArgumentException("부모 댓글이 존재하지 않습니다: " + replyDTO.getParentReplyId());
            }
            if (parentReply.getDepth() >= MAX_DEPTH) {
                throw new IllegalArgumentException("대댓글에는 댓글을 달 수 없습니다. 최대 1계층까지만 허용됩니다.");
            }
            if (!parentReply.getBoardId().equals(replyDTO.getBoardId())) {
                throw new IllegalArgumentException("다른 게시글에 달려있는 댓글에 대댓글은 못답니다.");
            }
            replyDTO.setDepth(1);
        } else {
            replyDTO.setDepth(0);
        }

        Reply reply = Reply.builder()
                .replyText(replyDTO.getReplyText())
                .replayer(replyDTO.getReplayer())
                .depth(replyDTO.getDepth())
                .board(BoardJpaEntity.builder().id(replyDTO.getBoardId()).build())
                .parentReply(replyDTO.getParentReplyId() == null ? null
                        : Reply.builder().id(replyDTO.getParentReplyId()).build())
                .build();

        return replyRepository.save(reply).getId();
    }

    /** 삭제되지 않은 댓글만 조회한다. 없으면 null 을 반환한다. */
    public ReplyDTO read(Long replyId) {
        log.info("댓글 조회: {}", replyId);
        return replyRepository.findByIdNotDeleted(replyId)
                .map(this::toDto)
                .orElse(null);
    }

    @Transactional
    public void modify(ReplyDTO replyDTO) {
        log.info("댓글 수정: {}", replyDTO);
        validateReplyText(replyDTO.getReplyText());

        if (read(replyDTO.getId()) == null) {
            throw new IllegalArgumentException("수정할 댓글이 존재하지 않습니다: " + replyDTO.getId());
        }

        // 기존 엔티티의 연관과 메타데이터를 보존하고 내용만 바꾼다.
        // modDate 는 BaseEntity 의 @PreUpdate 가 설정한다.
        replyRepository.findById(replyDTO.getId()).ifPresent(reply -> {
            reply.setReplyText(replyDTO.getReplyText());
            reply.setReplayer(replyDTO.getReplayer());
            replyRepository.save(reply);
        });
    }

    /** 논리적 삭제. 하위 댓글도 함께 삭제한다. */
    @Transactional
    public void remove(Long replyId) {
        log.info("댓글 삭제: {}", replyId);

        if (read(replyId) == null) {
            throw new IllegalArgumentException("삭제할 댓글이 존재하지 않습니다: " + replyId);
        }

        replyRepository.findById(replyId).ifPresent(reply -> {
            reply.markDeleted();
            replyRepository.save(reply);

            List<Reply> childReplies = replyRepository.findByParentReplyId(replyId);
            childReplies.forEach(child -> {
                child.markDeleted();
                replyRepository.save(child);
            });
        });
    }

    public PageResponseDTO<ReplyDTO> getListOfBoard(Long boardId, PageRequestDTO pageRequestDTO) {
        log.info("게시글 {}의 댓글 목록 조회, 페이지: {}", boardId, pageRequestDTO);

        Page<Reply> result = replyRepository.listOfBoard(boardId, toPageable(pageRequestDTO));
        List<ReplyDTO> dtoList = result.getContent().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return new PageResponseDTO<>(pageRequestDTO, dtoList, (int) result.getTotalElements());
    }

    /**
     * 계층형 댓글 목록.
     * 최상위 댓글만 페이징하고, 각 최상위 댓글의 대댓글은 전부 포함한다.
     */
    public PageResponseDTO<ReplyDTO> getHierarchicalListOfBoard(Long boardId, PageRequestDTO pageRequestDTO) {
        log.info("게시글 {}의 계층형 댓글 목록 조회, 페이지: {}", boardId, pageRequestDTO);

        Page<Reply> result = replyRepository.listOfBoardRootReplies(boardId, toPageable(pageRequestDTO));

        if (result.getTotalElements() == 0) {
            return PageResponseDTO.<ReplyDTO>of()
                    .page(pageRequestDTO.getPage())
                    .size(pageRequestDTO.getSize())
                    .dtoList(Collections.emptyList())
                    .total(0)
                    .build();
        }

        List<ReplyDTO> rootReplies = result.getContent().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        rootReplies.forEach(root -> root.setChildren(getChildReplies(root.getId())));

        return new PageResponseDTO<>(pageRequestDTO, rootReplies, (int) result.getTotalElements());
    }

    public List<ReplyDTO> getChildReplies(Long parentId) {
        return replyRepository.findByParentReplyId(parentId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public boolean hasChildReplies(Long replyId) {
        return replyRepository.hasChildReplies(replyId);
    }

    /** 게시글에 속한 모든 댓글을 논리적으로 삭제한다. */
    @Transactional
    public void deleteByBoardId(Long boardId) {
        replyRepository.findByBoard_Id(boardId).forEach(reply -> {
            reply.markDeleted();
            replyRepository.save(reply);
        });
    }

    // ---- 내부 ----

    private void validateReplyText(String replyText) {
        if (replyText == null || replyText.trim().isEmpty()) {
            throw new IllegalArgumentException("댓글 내용은 비어있을 수 없습니다.");
        }
        if (replyText.trim().length() > MAX_REPLY_LENGTH) {
            throw new IllegalArgumentException("댓글 내용은 " + MAX_REPLY_LENGTH + "자를 초과할 수 없습니다.");
        }
    }

    private Pageable toPageable(PageRequestDTO pageRequestDTO) {
        return PageRequest.of(
                pageRequestDTO.getPage() - 1,
                pageRequestDTO.getSize(),
                Sort.by("id").ascending());
    }

    private ReplyDTO toDto(Reply reply) {
        return ReplyDTO.builder()
                .id(reply.getId())
                .boardId(reply.getBoardId())
                .replyText(reply.getReplyText())
                .replayer(reply.getReplayer())
                .regDate(reply.getRegDate())
                .modDate(reply.getModDate())
                .depth(reply.getDepth())
                .parentReplyId(reply.getParentReplyId())
                .build();
    }
}
