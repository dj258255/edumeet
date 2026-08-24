package com.edu.edumeet.chat.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 채팅 지표. (#39)
 *
 * <h3>왜 WebSocketMessageBrokerStats 를 쓰지 않는가</h3>
 * Spring 이 같은 값을 이미 계산하지만 <b>전부 {@code String} 으로만 노출한다.</b>
 * <pre>
 *   String getClientInboundExecutorStatsInfo()   // "pool size = 8, active threads = 0, ..."
 * </pre>
 * 문자열을 파싱하면 Spring 이 형식을 바꾸는 순간 조용히 깨진다.
 * 세션·방·발행량은 <b>이벤트로 직접 센다.</b>
 *
 * <h3>fan-out 배수를 왜 따로 재는가</h3>
 * 발행 1건이 수신 N건이 되는 <b>배수가 브로드캐스트 비용의 본체</b>다.
 * 발행량만 보면 30명 방과 3,000명 방이 같아 보인다.
 */
@Component
@Slf4j
public class ChatMetrics {

    private final AtomicInteger activeSessions = new AtomicInteger();
    /** 목적지 → 구독자 수. 방이 비면 제거해서 게이지가 실제 활성 방을 가리키게 한다. */
    private final Map<String, AtomicInteger> subscribersByRoom = new ConcurrentHashMap<>();

    private final Counter publishedMessages;
    private final DistributionSummary fanoutRecipients;

    public ChatMetrics(MeterRegistry registry) {
        Gauge.builder("chat.sessions.active", activeSessions, AtomicInteger::get)
                .description("현재 열려 있는 STOMP 세션 수")
                .register(registry);

        Gauge.builder("chat.rooms.active", subscribersByRoom, Map::size)
                .description("구독자가 하나 이상인 방 수")
                .register(registry);

        this.publishedMessages = Counter.builder("chat.messages.published")
                .description("서버가 브로드캐스트한 메시지 수 (fan-out 이전)")
                .register(registry);

        this.fanoutRecipients = DistributionSummary.builder("chat.fanout.recipients")
                .description("발행 1건이 실제로 전달된 대상 수. 브로드캐스트 비용의 본체다")
                .publishPercentileHistogram()
                .register(registry);
    }

    public void sessionConnected() {
        activeSessions.incrementAndGet();
    }

    public void sessionDisconnected() {
        activeSessions.updateAndGet(n -> Math.max(0, n - 1));
    }

    public void subscribed(String destination) {
        subscribersByRoom.computeIfAbsent(destination, d -> new AtomicInteger()).incrementAndGet();
    }

    /**
     * 구독자가 0이 되면 항목 자체를 지운다.
     * 남겨두면 {@code chat.rooms.active} 가 "한 번이라도 쓰인 방" 을 세게 되어
     * 활성 방 수와 어긋난다.
     */
    public void unsubscribed(String destination) {
        subscribersByRoom.computeIfPresent(destination, (d, count) ->
                count.decrementAndGet() <= 0 ? null : count);
    }

    /** 발행 1건과 그 수신자 수를 함께 기록한다. */
    public void published(String destination) {
        publishedMessages.increment();
        AtomicInteger subscribers = subscribersByRoom.get(destination);
        fanoutRecipients.record(subscribers == null ? 0 : subscribers.get());
    }

    /** 세션이 끊길 때 그 세션의 구독을 정리하기 위해 필요하다. */
    public int subscriberCount(String destination) {
        AtomicInteger count = subscribersByRoom.get(destination);
        return count == null ? 0 : count.get();
    }
}
