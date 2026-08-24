package com.edu.edumeet.chat.service;

import com.edu.edumeet.chat.domain.ChatMessage;
import com.edu.edumeet.chat.dto.ChatMessageResponse;
import com.edu.edumeet.chat.repository.ChatMessageRepository;
import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.domain.SessionType;
import com.edu.edumeet.meeting.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

/**
 * 채팅 메시지 처리. (#33)
 *
 * <p>Phase 1 은 <b>동작 확인까지</b>다. 성능 측정과 백프레셔는 Phase 2 부터다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private static final int MAX_CONTENT_LENGTH = 1000;
    private static final int RECENT_LIMIT = 50;

    private final MeetingRepository meetingRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatArchiveQueue archiveQueue;

    /**
     * 메시지를 받아 브로드캐스트할 형태로 만든다. 저장 여부는 세션 형태가 정한다.
     *
     * <p>{@code publishedAt} 은 <b>저장 후가 아니라 반환 직전</b>에 찍는다.
     * 수신자가 재는 지연에 DB 왕복이 포함되어야 실제 체감과 맞는다.
     */
    @Transactional
    public ChatMessageResponse handle(Long meetingId, String senderEmail, String content) {
        String trimmed = validate(content);

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회의입니다: " + meetingId));

        if (meeting.getEndTime() != null) {
            throw new IllegalArgumentException("이미 종료된 회의입니다.");
        }

        if (shouldPersist(meeting.getSessionType())) {
            // 화상강의는 그대로 동기 저장한다. 정원 30명이라 발행량이 작아
            // 측정을 가리지 않고, 무엇보다 수업 기록은 유실되면 안 된다.
            chatMessageRepository.save(ChatMessage.of(meeting, senderEmail, trimmed));
        } else {
            // 방송은 발행 경로에서 저장하지 않는다. 큐에 넣고 배치가 가져간다. (#61)
            // 가득 차면 버린다 - 다시보기 채팅은 유실돼도 방송은 살아 있어야 한다.
            archiveQueue.offer(meetingId, senderEmail, trimmed,
                    offsetFrom(meeting));
        }

        return new ChatMessageResponse(
                meetingId, senderEmail, trimmed, System.currentTimeMillis());
    }

    /** 입장 시 보여줄 지난 대화. 저장하지 않는 BROADCAST 는 항상 빈 목록이다. */
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> recentMessages(Long meetingId) {
        return chatMessageRepository.findRecent(meetingId, PageRequest.of(0, RECENT_LIMIT))
                .stream()
                .sorted(Comparator.comparing(ChatMessage::getSentAt))   // 오래된 것부터 보여준다
                .map(m -> new ChatMessageResponse(
                        meetingId, m.getSenderEmail(), m.getContent(),
                        m.getSentAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()))
                .toList();
    }

    /**
     * 회의 시작 기준 경과 밀리초. 다시보기 재생 위치와 맞추는 데 쓴다. (#61)
     *
     * <p>시작 시각이 없으면 {@code null} 이다. 그런 회의는 다시보기가 없으므로
     * 여기서 예외를 던질 이유가 없다.
     */
    private Long offsetFrom(Meeting meeting) {
        if (meeting.getStartTime() == null) {
            return null;
        }
        long started = meeting.getStartTime().atZone(ZoneId.systemDefault())
                .toInstant().toEpochMilli();
        return Math.max(0, System.currentTimeMillis() - started);
    }

    /**
     * 발행 경로에서 동기로 저장하는가.
     *
     * <p><b>"저장하지 않는다" 가 아니라 "발행 경로에서 저장하지 않는다" 이다.</b> (#61)
     * 방송 채팅도 다시보기에 필요해서 저장한다. 다만 큐를 거쳐 배치로 넣는다.
     *
     * <p>처음에는 <i>"시청자 수천 명 × 초당 수십 메시지면 쓰기가 폭증한다"</i> 를
     * 근거로 아예 저장하지 않았다. <b>그 근거는 틀렸다</b> —
     * 쓰기는 fan-out 이 아니라 발행량이고, 시청자가 3,000명이어도
     * 메시지 1건은 <b>DB write 1건</b>이다. fan-out 은 읽기(전송) 쪽이다.
     *
     * <p>유효했던 근거는 다른 하나다. #43 에서 확인했듯
     * <b>발행 경로의 DB 쓰기는 브로드캐스트 측정을 가린다.</b>
     * 그건 제품 문제가 아니라 측정 문제이므로, 저장을 없애는 대신 경로를 옮긴다.
     */
    private boolean shouldPersist(SessionType sessionType) {
        // 정책을 여기서 비교하지 않는다. 세션 타입이 늘 때 이 자리를 빠뜨리면
        // 새 타입이 조용히 INTERACTIVE 와 다르게 동작한다. (#65)
        return sessionType.persistsChatInline();
    }

    private String validate(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("빈 메시지는 보낼 수 없습니다.");
        }
        String trimmed = content.strip();
        if (trimmed.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException(
                    "메시지가 너무 깁니다. (최대 %d자)".formatted(MAX_CONTENT_LENGTH));
        }
        return trimmed;
    }
}
