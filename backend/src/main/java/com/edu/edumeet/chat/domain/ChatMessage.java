package com.edu.edumeet.chat.domain;

import com.edu.edumeet.meeting.domain.Meeting;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 채팅 메시지. <b>INTERACTIVE 세션만 저장한다.</b> (#33)
 *
 * <p>BROADCAST(라이브방송)는 저장하지 않는다. 시청자 수천 명 × 초당 수십 메시지면
 * 쓰기가 폭증하는데, 방송 채팅은 원래 휘발성이다.
 * 이 분기가 없으면 <b>브로드캐스트 성능 측정이 DB 쓰기에 묻힌다.</b>
 */
@Entity
@Table(name = "chat_message",
       indexes = @Index(name = "idx_chat_message_meeting_sent", columnList = "meeting_id, sent_at"))
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id")
    private Meeting meeting;

    @Column(name = "sender_email", nullable = false, length = 255)
    private String senderEmail;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    /**
     * 회의 시작 시각 기준 경과 밀리초. 다시보기 재생 위치와 맞추는 데 쓴다. (#61)
     *
     * <p>{@code sentAt} 에서 계산할 수도 있지만 저장 시점에 넣어 둔다.
     * 방송 시작 시각이 나중에 보정되면 이미 저장된 채팅의 재생 위치가 전부 어긋난다.
     */
    @Column(name = "offset_millis")
    private Long offsetMillis;

    public static ChatMessage of(Meeting meeting, String senderEmail, String content) {
        return ChatMessage.builder()
                .meeting(meeting)
                .senderEmail(senderEmail)
                .content(content)
                .sentAt(LocalDateTime.now())
                .build();
    }

    /**
     * 다시보기용 상대 시각을 함께 담아 만든다. (#61)
     *
     * @param offsetMillis 회의 시작 기준 경과 밀리초. 시작 시각을 모르면 {@code null}
     */
    public static ChatMessage of(Meeting meeting, String senderEmail, String content,
                                 Long offsetMillis) {
        ChatMessage message = of(meeting, senderEmail, content);
        message.offsetMillis = offsetMillis;
        return message;
    }
}
