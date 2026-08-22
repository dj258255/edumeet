package com.edu.edumeet.openvidu.repository;

import com.edu.edumeet.openvidu.domain.MeetingParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    /**
     * 아직 나가지 않은 참가 기록을 전부 닫는다.
     *
     * <p>회의 종료나 룸 종료 시 호출한다. 이게 없으면 참가 기록이 영원히 활성으로 남아
     * <b>정원이 반환되지 않는다.</b> (#23)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MeetingParticipant p SET p.leftAt = :now " +
           "WHERE p.meeting.id = :meetingId AND p.leftAt IS NULL")
    int closeAllActive(@Param("meetingId") Long meetingId, @Param("now") LocalDateTime now);
}
