package com.edu.edumeet.meeting.repository;

import com.edu.edumeet.meeting.domain.CaptionSegment;
import com.edu.edumeet.meeting.dto.CaptionMeetingSummary;
import org.springframework.data.domain.Pageable;
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

    /**
     * final 자막이 있는 회의만 최근 발화 순으로. (#133)
     *
     * <p>자막이 하나도 없는 회의는 조인 결과에 아예 없으므로 자연히 빠진다.
     * "회의를 전부 가져와서 자막이 있는 것만 거른다" 가 아니라
     * <b>자막에서 출발해 회의로 올라간다.</b> 회의가 늘어도 스캔 대상은 자막이다.
     *
     * <p>정렬 기준을 {@code spokenAt} 으로 둔 이유 - {@code createdAt} 은 저장 시각이라
     * 배치가 밀리면 순서가 뒤집힌다. 사람이 기대하는 순서는 "언제 말했나" 다.
     */
    @Query("""
            SELECT new com.edu.edumeet.meeting.dto.CaptionMeetingSummary(
                       m.id, m.title, COUNT(c), MAX(c.spokenAt))
            FROM CaptionSegment c
            JOIN c.meeting m
            WHERE c.finalSegment = true
            GROUP BY m.id, m.title
            ORDER BY MAX(c.spokenAt) DESC, m.id DESC
            """)
    List<CaptionMeetingSummary> findMeetingsWithCaptions(Pageable pageable);
}
