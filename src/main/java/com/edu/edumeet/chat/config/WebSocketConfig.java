package com.edu.edumeet.chat.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * 채팅 STOMP 설정. (#33)
 *
 * <h3>왜 SimpleBroker 인가</h3>
 * 인메모리 단일 인스턴스 브로커다. 다중 인스턴스로 확장되지 않는다는 걸 알면서 고른다.
 * <b>처음부터 외부 브로커로 가면 무엇을 피했는지 말할 수 없다.</b>
 * 기본 구성이 몇 명에서 무너지는지 먼저 재고, 그 수치를 근거로 다음을 고른다.
 *
 * <h3>왜 SockJS 를 안 붙이는가</h3>
 * 부하 시험에서 <b>k6 는 STOMP 도 SockJS 도 모른다.</b> 프레임을 문자열로 조립해야 하므로
 * 순수 WebSocket 엔드포인트를 연다. 브라우저 폴백이 필요해지면 별도 엔드포인트를 추가한다.
 *
 * <h3>스레드 풀을 지금 손대지 않는 이유</h3>
 * Boot 기본값은 {@code applicationTaskExecutor} 이고 <b>큐 용량이 무한</b>이다.
 * 이건 알려진 위험이지만 <b>지금 고치면 무엇을 고쳤는지 잴 수 없다.</b>
 * Phase 2 에서 OOM 을 재현한 뒤에 제한을 건다.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Value("${front.url}")
    private String frontUrl;

    @Value("${front.url2}")
    private String frontUrl2;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 구독 대상. 방 하나가 /topic/rooms/{meetingId} 다.
        registry.enableSimpleBroker("/topic");
        // 클라이언트가 서버로 보낼 때의 접두사. @MessageMapping 이 이 뒤를 받는다.
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(frontUrl, frontUrl2);
    }

    /**
     * 인증은 여기서 한다. HTTP 필터 체인은 <b>핸드셰이크까지만</b> 관여하고
     * 그 뒤 STOMP 프레임에는 관여하지 않는다.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
