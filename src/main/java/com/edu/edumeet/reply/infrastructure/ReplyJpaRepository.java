package com.edu.edumeet.reply.infrastructure;

import com.edu.edumeet.reply.domain.Reply;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReplyJpaRepository extends JpaRepository<ReplyJpaEntity, Long> {

    @Query("select r from ReplyJpaEntity r where r.board.id = :boardId")
    Page<Reply> listOfBoard(Long boardId, Pageable pageable);
}
