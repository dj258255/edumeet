package com.edu.edumeet.openvidu.repository;

import com.edu.edumeet.openvidu.domain.Meeting;
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
}
