package com.edu.edumeet.openvidu.repository;

import com.edu.edumeet.openvidu.domain.MeetingParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipant, Long> {

    /** 현재 세션에 남아 있는(퇴장하지 않은) 참가자 수 */
    @Query("SELECT COUNT(p) FROM MeetingParticipant p " +
           "WHERE p.meeting.id = :meetingId AND p.leftAt IS NULL")
    long countActiveByMeetingId(@Param("meetingId") Long meetingId);

    @Query("SELECT p FROM MeetingParticipant p " +
           "WHERE p.meeting.id = :meetingId AND p.participantEmail = :email AND p.leftAt IS NULL")
    Optional<MeetingParticipant> findActive(@Param("meetingId") Long meetingId,
                                            @Param("email") String email);
}
