package com.edu.edumeet.chat.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

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
 * <h3>큐 상한을 왜 걸었나 (#43)</h3>
 * 기본 큐는 무한이다. 처음에는 "빠르게 발행하면 터진다" 고 가정했는데 <b>틀렸다.</b>
 * 구독자 150명에게 초당 36,000건을 4분간 밀어 넣어도 큐는 최대 525 에서 바로 빠졌다.
 *
 * <p>무한 큐가 위험해지는 조건은 <b>빠른 발행이 아니라 느린 소비</b>다.
 * Toxiproxy 로 연결당 5KB/s 로 조이자 <b>큐가 107만 개까지 쌓이고 2분 만에 OOM</b> 이 났다.
 *
 * <p>그때 Spring 의 자체 보호({@code sendTimeLimit} 초과 세션 종료)는 <b>작동했지만 부족했다.</b>
 * 세션 32개를 끊는 동안에도 큐는 계속 쌓였다. 보호가 <b>세션 단위(send 경로)에만 있고
 * 실행기 큐에는 없어서</b> 층이 하나 비어 있었다. 그 층을 채운다.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * 큐 상한. 무한이면 힙이 상한이 된다.
     *
     * <p>아웃바운드를 크게 잡는 이유는 fan-out 때문이다 - 발행 1건이 구독자 수만큼 작업이 된다.
     * 다만 <b>어떤 값이든 무한보다는 낫다.</b> 값 자체보다 상한이 있다는 사실이 중요하다.
     */
    private static final int INBOUND_QUEUE_CAPACITY = 2_000;
    private static final int OUTBOUND_QUEUE_CAPACITY = 20_000;

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
        registration.taskExecutor(boundedExecutor("chat-in-", INBOUND_QUEUE_CAPACITY));
    }

    /**
     * 아웃바운드가 붕괴 지점이다. fan-out 이 증폭기라 발행 1건이 구독자 수만큼 작업을 만든다.
     */
    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor(boundedExecutor("chat-out-", OUTBOUND_QUEUE_CAPACITY));
    }

    /**
     * 세션 단위 보호를 조인다. 큐 상한만으로는 부족하다 -
     * <b>느린 클라이언트 하나가 큐를 계속 채우는 것</b> 자체를 빨리 끊어야 한다.
     *
     * <p>기본값은 send 10초 / 버퍼 512KB 다. 기본값으로도 세션은 끊겼지만
     * 그 사이 큐가 107만 개까지 쌓였다. 더 빨리 판정한다.
     */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setSendTimeLimit(5_000);
        registration.setSendBufferSizeLimit(256 * 1024);
        registration.setMessageSizeLimit(64 * 1024);
    }

    /**
     * 상한이 있는 실행기.
     *
     * <p><b>{@code TaskExecutorRegistration} 에는 거부 정책을 설정하는 메서드가 없다.</b>
     * {@code corePoolSize}·{@code queueCapacity} 는 있어도 {@code RejectedExecutionHandler} 는 없다.
     * 그래서 실행기를 직접 만들어 넘긴다.
     *
     * <p>거부 정책은 {@link ThreadPoolExecutor.CallerRunsPolicy} 다.
     * 버리는 대신 <b>부르는 쪽이 직접 처리</b>하게 해서 역압을 위로 전달한다.
     * 채팅은 순서가 있는 대화라 조용히 버리면 대화가 깨진다 -
     * 느려지는 편이 낫다. 그리고 느려지는 것은 지표에 드러난다(큐 길이가 상한에 붙는다).
     */
    private ThreadPoolTaskExecutor boundedExecutor(String prefix, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(prefix);
        executor.setCorePoolSize(Runtime.getRuntime().availableProcessors() * 2);
        executor.setMaxPoolSize(Runtime.getRuntime().availableProcessors() * 4);
        executor.setQueueCapacity(queueCapacity);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 종료 시 큐에 남은 것을 버린다. 죽는 중에 붙잡고 있을 이유가 없다.
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
