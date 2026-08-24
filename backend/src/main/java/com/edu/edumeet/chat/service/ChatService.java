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
            chatMessageRepository.save(ChatMessage.of(meeting, senderEmail, trimmed));
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
     * BROADCAST 는 저장하지 않는다.
     *
     * <p>시청자 수천 명 × 초당 수십 메시지면 쓰기가 폭증한다. 방송 채팅은 원래 휘발성이고,
     * 무엇보다 <b>이 분기가 없으면 브로드캐스트 성능 측정이 DB 쓰기에 묻힌다.</b>
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
