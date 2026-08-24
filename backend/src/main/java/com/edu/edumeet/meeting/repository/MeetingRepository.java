package com.edu.edumeet.meeting.repository;

import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import com.edu.edumeet.meeting.domain.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {
    @Query("""
      select m
      from Meeting m
      where m.classRoom.id = :classId
      order by case when m.endTime is null then 0 else 1 end, m.endTime desc
    """)
    List<Meeting> findAllSortedByNullFirst(@Param("classId") Long classId);
    Optional<Meeting> findTopByClassRoomIdAndS3urlIsNotNullOrderByStartTimeDesc(Long classRoomId);
    Optional<Meeting> findTopByClassRoomIdOrderByStartTimeDesc(Long classRoomId);

    /**
     * 정원 검증을 위해 세션 행에 쓰기 잠금을 건다.
     *
     * "현재 인원을 세고 -> 정원과 비교하고 -> 참가를 기록한다"는 세 단계가
     * 원자적이지 않으면 동시 요청이 모두 정원 검사를 통과해 초과 입장이 발생한다.
     * 세션 행을 잠가 이 구간을 직렬화한다. (#2)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Meeting m WHERE m.id = :id")
    Optional<Meeting> findByIdForUpdate(@Param("id") Long id);
}
