package com.edu.edumeet.chat.controller;

import com.edu.edumeet.chat.dto.ChatMessageResponse;
import com.edu.edumeet.chat.metrics.ChatMetrics;
import com.edu.edumeet.chat.dto.ChatSendRequest;
import com.edu.edumeet.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * 채팅 수신·브로드캐스트. (#33)
 *
 * <pre>
 *   클라이언트 → /app/rooms/{meetingId}/send
 *   서버      → /topic/rooms/{meetingId}
 * </pre>
 *
 * <p><b>보낸 사람을 페이로드에서 읽지 않는다.</b> {@link Principal} 은
 * {@code StompAuthChannelInterceptor} 가 CONNECT 때 검증한 JWT 에서 나온다.
 * 페이로드를 믿으면 남의 이름으로 보낼 수 있다.
 *
 * <p>{@code @SendTo} 대신 {@link SimpMessagingTemplate} 을 쓰는 이유는
 * 목적지에 {@code meetingId} 가 들어가기 때문이다.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMetrics chatMetrics;

    @MessageMapping("/rooms/{meetingId}/send")
    public void send(@DestinationVariable Long meetingId,
                     ChatSendRequest request,
                     Principal principal) {

        ChatMessageResponse response =
                chatService.handle(meetingId, principal.getName(), request.content());

        String destination = "/topic/rooms/" + meetingId;
        messagingTemplate.convertAndSend(destination, response);

        // 발행 1건이 수신 N건이 되는 배수가 브로드캐스트 비용의 본체다. (#39)
        // 발행량만 세면 30명 방과 3,000명 방이 같아 보인다.
        chatMetrics.published(destination);
    }
}
