package com.edu.edumeet.meeting.service;

import com.edu.edumeet.chat.metrics.ChatMetrics;
import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.dto.CaptionBroadcast;
import com.edu.edumeet.meeting.dto.CaptionIngestRequest;
import com.edu.edumeet.meeting.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실시간 자막 브로드캐스트. (#65)
 *
 * <h3>왜 파이썬이 직접 뿌리지 않는가</h3>
 * <ul>
 *   <li><b>인가가 여기 있다.</b> 누가 이 방을 볼 수 있는지는 자바가 안다.
 *       파이썬이 직접 뿌리면 그 로직을 복제해야 하고, 복제된 인가는 반드시 어긋난다</li>
 *   <li><b>시청자는 이미 붙어 있다.</b> 채팅 때문에 WebSocket 이 이미 열려 있다.
 *       자막용 연결을 또 만들면 커넥션 한계가 두 배 빨리 온다</li>
 *   <li><b>fan-out 인프라가 여기 있다.</b> 큐 상한(#43)·지표(#39)·백프레셔</li>
 * </ul>
 *
 * <h3>채팅과 부하 형태가 반대다</h3>
 * <pre>
 *   채팅   발신자 N명 x 각자 가끔   → 발행량이 시청자 수에 비례
 *   자막   발신자 1명 x 끊임없이    → 발행량이 시청자 수와 무관
 * </pre>
 * 같은 fan-out 인프라를 쓰지만 <b>#43 에서 정한 큐 상한이 자막에 맞는 값인지는 다시 재봐야 안다.</b>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CaptionService {

    private static final int MAX_TEXT_LENGTH = 500;

    private final MeetingRepository meetingRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMetrics chatMetrics;

    /**
     * 자막을 방에 뿌린다.
     *
     * <p><b>저장하지 않는다.</b> 실시간 자막은 지나가면 끝이고,
     * 다시보기용 저장은 녹음과 함께 다룬다(#61). 발행 경로에 DB 쓰기를 넣으면
     * #43 에서 본 것처럼 <b>브로드캐스트 측정이 쓰기에 묻힌다.</b>
     */
    @Transactional(readOnly = true)
    public CaptionBroadcast broadcast(Long meetingId, CaptionIngestRequest request, long receivedAt) {
        String text = validate(request);

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회의입니다: " + meetingId));
        if (meeting.getEndTime() != null) {
            throw new IllegalArgumentException("이미 종료된 회의입니다.");
        }

        String destination = captionDestination(meetingId);
        CaptionBroadcast payload = new CaptionBroadcast(
                meetingId, text, request.sequence(), request.spokenAt(),
                receivedAt, System.currentTimeMillis());

        messagingTemplate.convertAndSend(destination, payload);
        chatMetrics.published(destination);
        return payload;
    }

    /** 채팅과 다른 목적지를 쓴다. 클라이언트가 자막만 구독하거나 끌 수 있어야 한다. */
    public static String captionDestination(Long meetingId) {
        return "/topic/rooms/" + meetingId + "/captions";
    }

    private String validate(CaptionIngestRequest request) {
        if (request == null || request.text() == null || request.text().isBlank()) {
            throw new IllegalArgumentException("빈 자막은 보낼 수 없습니다.");
        }
        String text = request.text().strip();
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "자막이 너무 깁니다. (최대 %d자)".formatted(MAX_TEXT_LENGTH));
        }
        return text;
    }
}
