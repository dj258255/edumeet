package com.edu.edumeet.chat.repository;

import com.edu.edumeet.chat.domain.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** 최근 메시지부터. 입장 시 지난 대화를 보여주는 용도다. */
    @Query("SELECT m FROM ChatMessage m WHERE m.meeting.id = :meetingId ORDER BY m.sentAt DESC")
    List<ChatMessage> findRecent(@Param("meetingId") Long meetingId, Pageable pageable);

    long countByMeetingId(Long meetingId);
}
