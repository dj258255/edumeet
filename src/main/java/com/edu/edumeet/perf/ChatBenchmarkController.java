package com.edu.edumeet.perf;

import com.edu.edumeet.chat.repository.ChatMessageRepository;
import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.config.jwt.JwtService;
import com.edu.edumeet.openvidu.domain.Meeting;
import com.edu.edumeet.openvidu.domain.MeetingParticipant;
import com.edu.edumeet.openvidu.domain.SessionType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 채팅 부하 측정 준비. <b>perf 프로파일 전용이다.</b> (#43)
 *
 * <p>기존 시더가 만드는 회의는 {@code endTime} 이 설정된 <b>종료된 회의</b>라
 * 채팅을 받지 않는다. 그리고 {@code MeetingParticipant} 를 시드하지 않아
 * STOMP SUBSCRIBE 단계에서 거절된다.
 *
 * <p>k6 는 JWT 를 만들 수 없으므로 여기서 발급한다.
 * 부하 전체가 같은 계정을 쓰지만 <b>세션은 연결마다 따로</b>라 fan-out 은 그대로 생긴다.
 */
@RestController
@RequestMapping("/api/perf/chat")
@Profile("perf")
@RequiredArgsConstructor
@Slf4j
public class ChatBenchmarkController {

    private static final String LOAD_EMAIL = "chat-load@edumeet.test";

    @PersistenceContext
    private EntityManager em;

    private final JwtService jwtService;
    private final ChatMessageRepository chatMessageRepository;

    /**
     * 진행 중인 회의와 참가 기록을 만들고 토큰을 준다.
     *
     * @param type INTERACTIVE(저장한다) / BROADCAST(저장하지 않는다).
     *             저장 여부가 큐 소진 속도를 바꾸므로 나눠서 잰다.
     */
    @PostMapping("/setup")
    @Transactional
    public ResponseEntity<Map<String, Object>> setup(
            @RequestParam(value = "type", defaultValue = "BROADCAST") SessionType type) {

        ClassRoom classRoom = ClassRoom.builder()
                .title("채팅 부하용 클래스")
                .description("부하 테스트 전용")
                .participantLimit(30)
                .isDeleted(false)
                .build();
        em.persist(classRoom);

        // endTime 을 비운다. 종료된 회의는 채팅을 받지 않는다.
        Meeting meeting = Meeting.builder()
                .classRoom(classRoom)
                .title("채팅 부하용 세션 " + type)
                .description("무한 큐 OOM 재현")
                .sessionType(type)
                .startTime(LocalDateTime.now())
                .build();
        em.persist(meeting);

        em.persist(MeetingParticipant.join(meeting, LOAD_EMAIL));
        em.flush();

        String token = jwtService.generateAccessToken(1L, LOAD_EMAIL);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("meetingId", meeting.getId());
        body.put("sessionType", type.name());
        body.put("persists", type == SessionType.INTERACTIVE);
        body.put("token", token);

        log.warn("채팅 부하 준비 - meetingId={}, type={}", meeting.getId(), type);
        return ResponseEntity.ok(body);
    }

    /** 저장된 메시지 수. INTERACTIVE 회차에서 DB 쓰기량을 확인하는 용도다. */
    @GetMapping("/stats/{meetingId}")
    public ResponseEntity<Map<String, Object>> stats(@PathVariable("meetingId") Long meetingId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("meetingId", meetingId);
        body.put("storedMessages", chatMessageRepository.countByMeetingId(meetingId));
        return ResponseEntity.ok(body);
    }
}
