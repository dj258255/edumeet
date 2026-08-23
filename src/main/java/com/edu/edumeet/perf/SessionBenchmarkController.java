package com.edu.edumeet.perf;

import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.exception.SessionCapacityExceededException;
import com.edu.edumeet.meeting.repository.MeetingParticipantRepository;
import com.edu.edumeet.meeting.repository.MeetingRepository;
import com.edu.edumeet.meeting.service.MeetingService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 세션 정원 제어를 실제 부하에서 검증한다.
 *
 * <p>이미 JUnit 동시성 테스트가 있지만 그것은 H2 위에서 20 스레드를 돌린 것이다.
 * H2 의 잠금과 MySQL InnoDB 의 {@code SELECT ... FOR UPDATE} 는 동작이 다르다.
 * InnoDB 에는 잠금 대기 타임아웃과 교착 상태 감지가 있어서, H2 에서 통과한 코드가
 * MySQL 에서 다르게 동작할 수 있다. 운영 DB 에서 다시 확인한다.
 *
 * <p>{@code lock=false} 로 호출하면 잠금 없는 구현을 탄다. 잠금이 실제로 무언가를
 * 막고 있음을 보이기 위한 대조군이다. 운영 코드에는 이 경로가 없다.
 */
@Profile("perf")
@RestController
@RequestMapping("/api/perf/sessions")
@RequiredArgsConstructor
public class SessionBenchmarkController {

    private final MeetingService meetingService;
    private final MeetingRepository meetingRepository;
    private final MeetingParticipantRepository meetingParticipantRepository;
    private final UnsafeJoinService unsafeJoinService;

    @PersistenceContext
    private EntityManager em;

    @PostMapping("/join")
    public ResponseEntity<Map<String, Object>> join(
            @RequestParam Long meetingId,
            @RequestParam String email,
            @RequestParam(defaultValue = "true") boolean lock) {
        try {
            Map<String, Object> token = lock
                    ? meetingService.joinSession(meetingId, email, false)
                    : unsafeJoinService.join(meetingId, email);
            return ResponseEntity.ok(token);
        } catch (SessionCapacityExceededException e) {
            // 전역 핸들러(CustomRestAdvice)도 409 로 바꿔주지만, 이 엔드포인트는
            // 측정 대상이므로 전역 설정 변경에 결과가 흔들리지 않게 직접 처리한다.
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "capacity", "message", e.getMessage()));
        }
    }

    /** 시드로 만든 세션의 id. 부하 스크립트가 하드코딩하지 않게 한다. */
    @GetMapping("/seeded")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> seeded() {
        Meeting meeting = meetingRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("시드된 세션이 없다"));
        return ResponseEntity.ok(Map.of(
                "meetingId", meeting.getId(),
                "limit", meeting.participantLimit(),
                "sessionType", meeting.getSessionType().name()));
    }

    /** 참가 기록을 지우고 정원을 다시 설정한다. 측정 회차 사이에 호출한다. */
    @PostMapping("/reset")
    @Transactional
    public ResponseEntity<Map<String, Object>> reset(
            @RequestParam Long meetingId,
            @RequestParam(defaultValue = "30") int limit) {

        em.createQuery("DELETE FROM MeetingParticipant p WHERE p.meeting.id = :id")
                .setParameter("id", meetingId).executeUpdate();

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("세션 없음: " + meetingId));
        em.createQuery("UPDATE ClassRoom c SET c.participantLimit = :n WHERE c.id = :id")
                .setParameter("n", limit)
                .setParameter("id", meeting.getClassRoom().getId())
                .executeUpdate();

        em.flush();
        em.clear();
        return ResponseEntity.ok(Map.of("meetingId", meetingId, "limit", limit, "active", 0));
    }

    /** 현재 참가자 수. 부하가 끝난 뒤 정원 초과가 있었는지 확인한다. */
    @GetMapping("/state")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> state(@RequestParam Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("세션 없음: " + meetingId));
        long active = meetingParticipantRepository.countActiveByMeetingId(meetingId);
        int limit = meeting.participantLimit();
        return ResponseEntity.ok(Map.of(
                "meetingId", meetingId,
                "limit", limit,
                "active", active,
                "overflow", Math.max(0, active - limit)));
    }
}
