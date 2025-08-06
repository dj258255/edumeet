package com.edu.edumeet.reply.infrastructure;

import com.edu.edumeet.reply.domain.Reply;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReplyJpaRepository extends JpaRepository<ReplyJpaEntity, Long> {
    /**
     * 특정 게시글의 댓글 목록 조회 (페이징)
     */
    @Query("select r from ReplyJpaEntity r where r.board.id = :board_id and r.deletedAt is null")
    Page<ReplyJpaEntity> listOfBoard(@Param("board_id") Long board_id, Pageable pageable);
    
    /**
     * 특정 게시글의 최상위 댓글만 조회 (페이징)
     */
    @Query("select r from ReplyJpaEntity r where r.board.id = :board_id and r.parentReply is null and r.deletedAt is null")
    Page<ReplyJpaEntity> listOfBoardRootReplies(@Param("board_id") Long board_id, Pageable pageable);
    
    /**
     * 특정 부모 댓글의 대댓글 목록 조회
     */
    @Query("select r from ReplyJpaEntity r where r.parentReply.id = :parent_id and r.deletedAt is null order by r.id asc")
    List<ReplyJpaEntity> findByParentReplyId(@Param("parent_id") Long parent_id);

    /**
     * 특정 게시글의 모든 댓글 조회
     */
    @Query("select r from ReplyJpaEntity r where r.board.id = :board_id")
    List<ReplyJpaEntity> findByBoard_Id(@Param("board_id") Long board_id);
    
    /**
     * 특정 게시글의 모든 댓글 삭제 (물리적 삭제)
     */
    void deleteByBoard_Id(Long board_id);
    
    /**
     * ID로 댓글 조회 (삭제되지 않은 댓글만)
     */
    @Query("select r from ReplyJpaEntity r where r.id = :id and r.deletedAt is null")
    Optional<ReplyJpaEntity> findByIdNotDeleted(@Param("id") Long id);
    
    /**
     * 특정 댓글이 대댓글을 가지고 있는지 확인
     */
    @Query("select count(r) > 0 from ReplyJpaEntity r where r.parentReply.id = :parent_id and r.deletedAt is null")
    boolean hasChildReplies(@Param("parent_id") Long parent_id);
}