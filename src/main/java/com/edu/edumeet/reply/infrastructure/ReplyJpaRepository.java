package com.edu.edumeet.reply.infrastructure;

import com.edu.edumeet.reply.domain.Reply;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReplyJpaRepository extends JpaRepository<ReplyJpaEntity, Long> {
    @Query("select r from ReplyJpaEntity r where r.board.id = :board_id")
    Page<ReplyJpaEntity> listOfBoard(@Param("board_id") Long board_id, Pageable pageable);

    void deleteByBoard_Id(Long board_id);
}