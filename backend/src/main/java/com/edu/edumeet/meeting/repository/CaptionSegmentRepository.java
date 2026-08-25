package com.edu.edumeet.meeting.repository;

import com.edu.edumeet.meeting.domain.CaptionSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface CaptionSegmentRepository extends JpaRepository<CaptionSegment, Long> {

    long countByMeetingId(Long meetingId);

    boolean existsByMeetingIdAndSequence(Long meetingId, Long sequence);

    @Query("""
            SELECT c.sequence FROM CaptionSegment c
            WHERE c.meeting.id = :meetingId
              AND c.sequence IN :sequences
            """)
    Set<Long> findExistingSequences(@Param("meetingId") Long meetingId,
                                    @Param("sequences") Collection<Long> sequences);

    /**
     * 회의 후 transcript 생성 순서. (#131)
     *
     * <p>sequence 가 있으면 그것이 기준이다. sequence 가 없는 과거/비정상 입력은
     * spokenAt 과 id 로 뒤에 붙인다. 여기서 순서가 흔들리면 요약 결과도 흔들린다.
     */
    @Query("""
            SELECT c FROM CaptionSegment c
            WHERE c.meeting.id = :meetingId
              AND c.finalSegment = true
            ORDER BY
              CASE WHEN c.sequence IS NULL THEN 1 ELSE 0 END,
              c.sequence ASC,
              c.spokenAt ASC,
              c.id ASC
            """)
    List<CaptionSegment> findTranscriptSegments(@Param("meetingId") Long meetingId);
}
