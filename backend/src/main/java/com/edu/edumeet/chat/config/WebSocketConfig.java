package com.edu.edumeet.chat.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
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
    private final MeterRegistry meterRegistry;

    @Value("${front.url}")
    private String frontUrl;

    @Value("${front.url2}")
    private String frontUrl2;

    /**
     * 하트비트 주기(밀리초). 서버가 보내는 주기 / 서버가 기대하는 주기.
     *
     * <p><b>왜 켜는가.</b> 채팅은 조용한 구간이 길다. 수업 중 1분 동안 아무도
     * 말을 안 하는 것은 흔하다. 그러면 중간의 프록시가 유휴 연결로 보고 끊는다 -
     * nginx {@code proxy_read_timeout} 기본값이 60초다.
     *
     * <p>실측했다. 하트비트 없이 조용한 연결 3개를 90초 유지했더니
     * <b>60.9초에 전부 끊겼다.</b> 그때는 {@code proxy_read_timeout} 을 3600초로
     * 늘려서 막았는데, 그건 <b>죽은 연결도 한 시간 잡고 있겠다</b>는 뜻이다.
     *
     * <p>하트비트가 있으면 유휴가 아니게 되므로 프록시 타임아웃을 짧게 둘 수 있고,
     * <b>죽은 연결을 빨리 걷어낼 수 있다.</b> 이쪽이 본래 해법이다.
     *
     * <p>25초로 잡은 이유 — 60초 타임아웃의 절반보다 작아야 한 번 놓쳐도 살아남는다.
     * 너무 짧으면 연결 수만큼 프레임이 늘어난다. 500 연결이면 초당 20프레임이다.
     */
    private static final long[] HEARTBEAT = {25_000L, 25_000L};

    /** 세션 안 발행 순서를 지킬지. 대가는 처리량이라 재고 정한다. (#157) */
    @Value("${edumeet.chat.preserve-publish-order:false}")
    private boolean preservePublishOrder;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 구독 대상. 방 하나가 /topic/rooms/{meetingId} 다.
        //
        // ★ SimpleBroker 의 하트비트는 TaskScheduler 가 있어야 동작한다. (#106)
        //   setHeartbeatValue 만 주고 스케줄러를 안 주면 조용히 무시된다 -
        //   설정은 있는데 프레임이 안 나가는, 이 저장소에서 여러 번 본 모양이다.
        registry.enableSimpleBroker("/topic")
                .setHeartbeatValue(HEARTBEAT)
                .setTaskScheduler(heartbeatScheduler());
        // 세션 안 발행 순서 보장. 기본은 끔.
        //
        //   아웃바운드 채널은 스레드 풀이라 같은 세션으로 가는 메시지의 순서가 보장되지 않는다.
        //   켜면 세션마다 직렬화해 순서를 지키는 대신 처리량을 낸다.
        //   상수로 두면 켜고 끄며 잴 수 없어서 설정으로 뺐다. (#157)
        registry.setPreservePublishOrder(preservePublishOrder);
        // 클라이언트가 서버로 보낼 때의 접두사. @MessageMapping 이 이 뒤를 받는다.
        registry.setApplicationDestinationPrefixes("/app");
    }

    /** 하트비트 전용 스케줄러. 한 스레드면 충분하다 - 프레임을 밀어 넣기만 한다. */
    @Bean
    public TaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("stomp-heartbeat-");
        scheduler.initialize();
        return scheduler;
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
        registerQueueMetrics(prefix, queueCapacity, executor);
        return executor;
    }

    /**
     * 큐 길이를 지표로 낸다. (#139)
     *
     * <p><b>이 메서드가 없는 동안 위 JavaDoc 은 거짓이었다.</b>
     * "느려지는 것은 지표에 드러난다(큐 길이가 상한에 붙는다)" 고 적어 뒀는데,
     * 그 지표가 없었다.
     *
     * <p>왜 없었나 - Spring Boot 의 executor 계측 자동 설정은 <b>빈</b>만 계측한다.
     * 이 실행기는 {@code configureClientOutboundChannel} 안에서 직접 만들어지므로
     * 빈이 아니고, 따라서 아무도 계측하지 않았다.
     *
     * <p>같은 모양을 여덟 번 만났다({@code docs/ops/07-declared-but-unused.md}).
     * 이번 것은 <b>주석이 존재를 주장한 지표</b>였다.
     *
     * <p>{@code capacity} 를 함께 내는 이유 - 경보를 "큐 15,000개" 같은 절대값으로 쓰면
     * 상한을 바꿀 때 경보가 조용히 무의미해진다. <b>비율로 물어보게</b> 한다.
     */
    private void registerQueueMetrics(String prefix, int queueCapacity, ThreadPoolTaskExecutor executor) {
        String channel = prefix.replace("chat-", "").replace("-", "");
        Gauge.builder("chat.channel.queued", executor,
                        e -> e.getThreadPoolExecutor().getQueue().size())
                .tag("channel", channel)
                .description("STOMP 채널 실행기에 쌓인 작업 수. 아웃바운드가 붕괴 지점이다")
                .register(meterRegistry);
        Gauge.builder("chat.channel.capacity", executor, e -> queueCapacity)
                .tag("channel", channel)
                .description("그 채널의 큐 상한. 경보는 절대값이 아니라 이것과의 비율로 본다")
                .register(meterRegistry);
        Gauge.builder("chat.channel.active", executor,
                        e -> e.getThreadPoolExecutor().getActiveCount())
                .tag("channel", channel)
                .description("작업 중인 스레드 수. 아웃바운드 고갈은 느린 클라 8명부터 시작한다")
                .register(meterRegistry);
    }
}
