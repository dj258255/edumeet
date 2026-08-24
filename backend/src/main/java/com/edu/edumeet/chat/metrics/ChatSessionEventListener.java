package com.edu.edumeet.chat.metrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * STOMP 생명주기 이벤트를 지표로 옮긴다. (#39)
 *
 * <h3>왜 구독을 직접 추적하는가</h3>
 * {@code SessionUnsubscribeEvent} 는 <b>목적지를 담지 않는다.</b> 구독 id 만 온다.
 * 그리고 세션이 그냥 끊기면 UNSUBSCRIBE 없이 사라진다.
 * 그래서 (세션, 구독id) → 목적지를 들고 있다가 정리한다.
 * 이걸 안 하면 <b>방이 비었는데도 구독자 수가 줄지 않아 fan-out 배수가 계속 부풀려진다.</b>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatSessionEventListener {

    private final ChatMetrics chatMetrics;

    /** "sessionId:subscriptionId" → destination */
    private final Map<String, String> destinationBySubscription = new ConcurrentHashMap<>();

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        chatMetrics.sessionConnected();
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }
        destinationBySubscription.put(key(accessor.getSessionId(), accessor.getSubscriptionId()), destination);
        chatMetrics.subscribed(destination);
    }

    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination =
                destinationBySubscription.remove(key(accessor.getSessionId(), accessor.getSubscriptionId()));
        if (destination != null) {
            chatMetrics.unsubscribed(destination);
        }
    }

    /**
     * 세션이 끊기면 그 세션의 구독을 전부 정리한다.
     * UNSUBSCRIBE 없이 연결이 끊기는 경우가 정상 경로다 - 브라우저를 닫으면 그렇게 된다.
     */
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        chatMetrics.sessionDisconnected();

        String prefix = event.getSessionId() + ":";
        destinationBySubscription.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(prefix)) {
                chatMetrics.unsubscribed(entry.getValue());
                return true;
            }
            return false;
        });
    }

    private String key(String sessionId, String subscriptionId) {
        return sessionId + ":" + subscriptionId;
    }
}
