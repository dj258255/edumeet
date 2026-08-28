package com.edu.edumeet.meeting.service;

import com.edu.edumeet.chat.metrics.ChatMetrics;
import com.edu.edumeet.meeting.dto.CaptionBroadcast;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 자막을 요청 스레드가 아닌 전용 스레드에서 내보낸다. (#151)
 *
 * <h3>왜 갈랐나</h3>
 * {@code brokerChannel} 에는 실행기가 없어서 {@code convertAndSend} 를 부른 스레드가
 * 그대로 {@code clientOutboundChannel.send()} 까지 간다. 아웃바운드가 포화하면
 * 거기 걸린 {@code CallerRunsPolicy} 때문에 <b>부른 스레드가 전송을 떠안는다.</b>
 *
 * <p>자막은 파이썬 STT 가 HTTP 로 밀어 넣으므로 그 스레드가 <b>Tomcat 요청 스레드</b>였다.
 * 즉 자막이 밀리면 채팅이 아니라 <b>REST API 전체</b>가 막혔다
 * (실측 {@code http-nio-auto-1-exec-1}, {@code BackpressureLandingTest}).
 *
 * <h3>왜 채팅과 정책이 달라야 하나</h3>
 * <pre>
 *   채팅   이어지는 대화다. 버리면 말이 끊긴다  →  안 버리고 느려진다(CallerRuns)
 *   자막   화면의 음성과 맞아야 한다           →  밀린 것을 버리고 최신을 살린다
 * </pre>
 * <b>5초 밀린 자막은 없는 것보다 나쁘다.</b> 화면에는 이미 다음 말이 지나갔는데
 * 뒤늦게 옛 자막이 뜨면 사용자는 그것을 지금 말로 읽는다.
 *
 * <h3>버려도 되는 이유</h3>
 * 전사는 {@link CaptionArchiveQueue} 가 따로 들고 있다.
 * <b>발행 경로에서 버려도 원본이 사라지지 않는다.</b> 다시보기와 요약 입력은 그쪽에서 나온다.
 *
 * <h3>버린 것은 반드시 센다</h3>
 * 조용히 버리면 "자막이 원래 그렇게 띄엄띄엄 나오나 보다" 가 된다.
 * {@code caption.broadcast.dropped} 가 0 을 넘으면 경보한다.
 */
@Component
@Slf4j
public class CaptionBroadcastQueue {

    /**
     * 상한. 자막은 초당 몇 조각이라 이 정도면 정상 상태에서는 절대 안 찬다.
     *
     * <p>크게 잡을 이유가 없다 - <b>깊은 큐는 오래된 자막을 오래 붙잡고 있겠다는 뜻</b>이고,
     * 그것은 이 경로가 지켜야 하는 최신성과 정확히 반대다.
     */
    private static final int CAPACITY = 256;

    private final Deque<Pending> queue = new ArrayDeque<>(CAPACITY);
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMetrics chatMetrics;
    private final Counter dropped;
    private final Counter published;

    private Thread worker;
    private volatile boolean running = true;

    public CaptionBroadcastQueue(SimpMessagingTemplate messagingTemplate,
                                 ChatMetrics chatMetrics,
                                 MeterRegistry registry) {
        this.messagingTemplate = messagingTemplate;
        this.chatMetrics = chatMetrics;

        Gauge.builder("caption.broadcast.queued", queue, q -> {
                    synchronized (queue) { return q.size(); }
                })
                .description("발행 대기 중인 자막 수. 여기가 늘면 화면 자막이 밀리고 있다")
                .register(registry);
        Gauge.builder("caption.broadcast.capacity", this, q -> CAPACITY)
                .description("발행 큐 상한. 경보는 절대값이 아니라 이것과의 비율로 본다")
                .register(registry);
        this.dropped = Counter.builder("caption.broadcast.dropped")
                .description("큐가 차서 버린 자막 수. 화면에서만 사라지고 전사에는 남는다")
                .register(registry);
        this.published = Counter.builder("caption.broadcast.published")
                .description("실제로 내보낸 자막 수").register(registry);
    }

    @PostConstruct
    void start() {
        worker = new Thread(this::drainForever, "caption-out-1");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * 발행을 맡긴다. <b>절대 요청 스레드를 막지 않는다.</b>
     *
     * <p>가득 차면 <b>가장 오래된 것을 버리고</b> 새 것을 넣는다.
     * 새 것을 버리면 화면이 과거에 멈추기 때문이다.
     *
     * @return 무언가를 버렸으면 false
     */
    public boolean offer(String destination, CaptionBroadcast payload) {
        boolean evicted = false;
        synchronized (queue) {
            if (queue.size() >= CAPACITY) {
                queue.pollFirst();
                evicted = true;
            }
            queue.addLast(new Pending(destination, payload));
            queue.notifyAll();
        }
        if (evicted) {
            dropped.increment();
            log.warn("자막 발행 큐가 가득 차 가장 오래된 조각을 버렸다. destination={}", destination);
        }
        return !evicted;
    }

    private void drainForever() {
        while (running) {
            Pending next;
            synchronized (queue) {
                while (running && queue.isEmpty()) {
                    try {
                        queue.wait(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (!running) return;
                next = queue.pollFirst();
            }
            if (next == null) continue;
            try {
                messagingTemplate.convertAndSend(next.destination(), next.payload());
                chatMetrics.published(next.destination());
                published.increment();
            } catch (Exception e) {
                // 여기서 죽으면 이후 자막이 통째로 멈춘다. 한 건을 포기하고 계속 돈다.
                log.warn("자막 발행 실패. destination={}", next.destination(), e);
            }
        }
    }

    @PreDestroy
    void stop() {
        running = false;
        synchronized (queue) { queue.notifyAll(); }
        if (worker != null) worker.interrupt();
    }

    /** 시험용. 지금 대기 중인 조각 수. */
    public int queuedCount() {
        synchronized (queue) { return queue.size(); }
    }

    private record Pending(String destination, CaptionBroadcast payload) {}
}
