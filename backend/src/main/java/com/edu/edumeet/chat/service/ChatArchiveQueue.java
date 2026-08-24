package com.edu.edumeet.chat.service;

import com.edu.edumeet.chat.domain.ChatMessage;
import com.edu.edumeet.chat.repository.ChatMessageRepository;
import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.repository.MeetingRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * 방송 채팅을 발행 경로 밖에서 저장한다. (#61)
 *
 * <pre>
 *   전    발행 -> DB write(동기) -> 브로드캐스트     쓰기가 지연에 직접 들어간다
 *   후    발행 -> 브로드캐스트                       즉시
 *              ↘ 큐 -> 배치 flush -> DB             비동기
 * </pre>
 *
 * <p><b>왜 저장을 되살리나.</b> 다시보기에서 라이브 채팅이 필요하다.
 * 트위치·치지직·유튜브가 모두 하는 것이고 없으면 VOD 가 반쪽이 된다.
 *
 * <p><b>왜 발행 경로에서 빼나.</b> #43 에서 확인했듯 발행 경로의 DB 쓰기는
 * 브로드캐스트 측정을 가린다. 그건 제품 문제가 아니라 측정 문제다.
 * 그래서 <b>"저장하지 않는다" 가 아니라 "발행 경로에서 저장하지 않는다"</b> 다.
 *
 * <p><b>★ 큐에 상한이 있다.</b> #43 에서 무한 큐로 84초 만에 OOM 을 냈다
 * (느린 소비자 조건, 큐 최대 1,077,906). 같은 실수를 다시 하지 않는다.
 * 가득 차면 <b>버린다</b> — 다시보기 채팅은 유실돼도 서비스가 죽지 않지만,
 * 힙이 터지면 방송 자체가 죽는다. <b>무엇을 지킬지 먼저 정한다.</b>
 */
@Component
@Slf4j
public class ChatArchiveQueue {

    /**
     * 큐 상한. 초당 100건이 들어와도 20초를 버틴다.
     *
     * <p>이 값의 근거는 "얼마나 버틸까" 가 아니라 <b>"터지면 무엇을 잃는가"</b> 다.
     * 2,000건 × 메시지당 대략 1KB = 2MB 남짓이라 힙에 부담이 없다.
     * 배치가 이보다 오래 밀리면 DB 쪽에 다른 문제가 있는 것이고,
     * 그때는 채팅을 더 쌓는 것보다 버리고 지표로 알리는 편이 낫다.
     */
    private static final int CAPACITY = 2_000;

    /** 한 번에 저장할 최대 건수. 너무 크면 트랜잭션이 길어지고 락을 오래 잡는다. */
    private static final int BATCH_SIZE = 200;

    private final BlockingQueue<Pending> queue = new ArrayBlockingQueue<>(CAPACITY);

    private final MeetingRepository meetingRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final Counter enqueued;
    private final Counter dropped;
    private final Counter persisted;

    private final boolean enabled;

    public ChatArchiveQueue(MeetingRepository meetingRepository,
                            ChatMessageRepository chatMessageRepository,
                            MeterRegistry registry,
                            @Value("${edumeet.chat.archive.enabled:true}") boolean enabled) {
        this.meetingRepository = meetingRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.enabled = enabled;

        Gauge.builder("chat.archive.queued", queue, java.util.Collection::size)
                .description("저장 대기 중인 방송 채팅 수")
                .register(registry);
        Gauge.builder("chat.archive.capacity", this, q -> CAPACITY)
                .description("큐 상한. queued 가 여기 붙으면 버리고 있다")
                .register(registry);
        this.enqueued = Counter.builder("chat.archive.enqueued")
                .description("큐에 넣은 수").register(registry);
        this.dropped = Counter.builder("chat.archive.dropped")
                .description("큐가 가득 차 버린 수. 0 이 아니면 배치가 못 따라가고 있다")
                .register(registry);
        this.persisted = Counter.builder("chat.archive.persisted")
                .description("실제로 저장한 수").register(registry);
    }

    /** 큐에 넣는다. <b>가득 차면 버리고 false 를 준다. 절대 막지 않는다.</b> */
    public boolean offer(Long meetingId, String senderEmail, String content, Long offsetMillis) {
        if (!enabled) {
            return false;
        }
        boolean accepted = queue.offer(new Pending(meetingId, senderEmail, content, offsetMillis));
        if (accepted) {
            enqueued.increment();
        } else {
            dropped.increment();
            // 건건이 로그를 남기면 큐가 넘칠 때 로그가 먼저 시스템을 잡아먹는다.
            // 지표(chat.archive.dropped)로 보고, 로그는 남기지 않는다.
        }
        return accepted;
    }

    /**
     * 배치로 저장한다.
     *
     * <p>주기 1초는 시작값이다. 다시보기는 실시간이 아니므로 몇 초 늦어도 된다.
     * 짧게 잡으면 트랜잭션이 잦아지고 길게 잡으면 큐가 길어진다 —
     * {@code chat.archive.queued} 와 {@code dropped} 를 보고 조정한다.
     */
    @Scheduled(fixedDelayString = "${edumeet.chat.archive.flush-interval-ms:1000}")
    public void flush() {
        List<Pending> batch = new ArrayList<>(BATCH_SIZE);
        queue.drainTo(batch, BATCH_SIZE);
        if (batch.isEmpty()) {
            return;
        }
        try {
            persist(batch);
        } catch (Exception e) {
            // 저장 실패로 브로드캐스트가 멈추면 안 된다. 다시보기가 반쪽이 되는 것과
            // 방송이 죽는 것 중 무엇이 나쁜지는 분명하다.
            log.warn("다시보기 채팅 배치 저장 실패 - {}건 유실. {}", batch.size(), e.toString());
        }
    }

    @Transactional
    protected void persist(List<Pending> batch) {
        // 회의는 배치 안에서 몇 개 안 된다. 건마다 조회하면 N+1 이다.
        Map<Long, Meeting> meetings = new HashMap<>();
        List<ChatMessage> rows = new ArrayList<>(batch.size());

        for (Pending p : batch) {
            Meeting meeting = meetings.computeIfAbsent(
                    p.meetingId(), id -> meetingRepository.findById(id).orElse(null));
            if (meeting == null) {
                continue;   // 회의가 지워졌다. 채팅만 남길 이유가 없다
            }
            rows.add(ChatMessage.of(meeting, p.senderEmail(), p.content(), p.offsetMillis()));
        }
        if (!rows.isEmpty()) {
            chatMessageRepository.saveAll(rows);
            persisted.increment(rows.size());
        }
    }

    /** 종료 시 남은 것을 한 번 더 비운다. 있는 것을 버릴 이유는 없다. */
    @PreDestroy
    public void drainOnShutdown() {
        int remaining = queue.size();
        if (remaining > 0) {
            log.info("종료 - 다시보기 채팅 {}건 남아 있어 비운다", remaining);
            while (!queue.isEmpty()) {
                flush();
            }
        }
    }

    /** 테스트에서 배치를 기다리지 않고 확인할 때 쓴다. */
    public int queuedCount() {
        return queue.size();
    }

    private record Pending(Long meetingId, String senderEmail, String content, Long offsetMillis) {}
}
