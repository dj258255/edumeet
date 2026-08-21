package com.edu.edumeet.perf;

import com.edu.edumeet.openvidu.domain.Meeting;
import com.edu.edumeet.openvidu.domain.MeetingParticipant;
import com.edu.edumeet.openvidu.exception.SessionCapacityExceededException;
import com.edu.edumeet.openvidu.repository.MeetingParticipantRepository;
import com.edu.edumeet.openvidu.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * 잠금 없이 참가시키는 구현. 정원 제어의 대조군이다.
 *
 * <p>{@link com.edu.edumeet.openvidu.service.OpenviduService#joinSession} 에서
 * {@code findByIdForUpdate} 를 {@code findById} 로 바꾼 것 외에는 동일하다.
 *
 * <p>왜 별도 빈인가 — Spring 의 {@code @Transactional} 은 프록시로 동작해서
 * 같은 클래스 안에서 호출하면 적용되지 않는다. 컨트롤러 안에 두면 트랜잭션 없이
 * 실행되어 "잠금만 뺀 같은 코드"가 아니게 된다. 대조군이 오염되면 비교가 무의미하다.
 *
 * <p>perf 프로파일 전용이다.
 */
@Profile("perf")
@Service
@RequiredArgsConstructor
public class UnsafeJoinService {

    private final MeetingRepository meetingRepository;
    private final MeetingParticipantRepository meetingParticipantRepository;

    @Transactional
    public Map<String, Object> join(Long meetingId, String email) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("세션 없음: " + meetingId));

        Optional<MeetingParticipant> already =
                meetingParticipantRepository.findActive(meetingId, email);

        if (already.isEmpty()) {
            if (meeting.hasParticipantLimit()) {
                // 세는 시점과 기록하는 시점 사이에 다른 트랜잭션이 끼어들 수 있다.
                long current = meetingParticipantRepository.countActiveByMeetingId(meetingId);
                if (current >= meeting.participantLimit()) {
                    throw new SessionCapacityExceededException(
                            "정원이 가득 찼습니다. (정원 " + meeting.participantLimit() + "명)");
                }
            }
            meetingParticipantRepository.save(MeetingParticipant.join(meeting, email));
        }
        return Map.of("joined", true);
    }
}
