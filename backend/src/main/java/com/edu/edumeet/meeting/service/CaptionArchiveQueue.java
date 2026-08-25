package com.edu.edumeet.meeting.service;

import com.edu.edumeet.meeting.domain.CaptionSegment;
import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.repository.CaptionSegmentRepository;
import com.edu.edumeet.meeting.repository.MeetingRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * final 자막을 발행 경로 밖에서 저장한다. (#131)
 *
 * <pre>
 *   전    STT -> Java -> STOMP                       저장 없음
 *   나쁜 후 STT -> Java -> DB write -> STOMP          DB 지연이 자막 지연이 됨
 *   후    STT -> Java -> STOMP
 *                    ↘ 유계 큐 -> 배치 저장 -> transcript
 * </pre>
 *
 * <p>자막은 접근성 hot path 다. 화면 표시는 DB 쓰기를 기다리지 않아야 한다.
 * 반대로 요약은 회의 후 작업이라 몇 초 늦어도 된다.
 */
@Component
@Slf4j
public class CaptionArchiveQueue {

    /** 초당 20조각이면 200초를 버틴다. 이 이상 밀리면 저장소 장애로 본다. */
    private static final int CAPACITY = 4_000;

    /** 한 번에 저장할 최대 건수. 너무 크면 트랜잭션이 길어지고 중복 확인 집합도 커진다. */
    private static final int BATCH_SIZE = 200;

    private final BlockingQueue<Pending> queue = new ArrayBlockingQueue<>(CAPACITY);

    private final MeetingRepository meetingRepository;
    private final CaptionSegmentRepository captionSegmentRepository;
    private final TransactionTemplate transactionTemplate;
    private final Counter enqueued;
    private final Counter dropped;
    private final Counter persisted;
    private final Counter duplicates;
    private final boolean enabled;

    public CaptionArchiveQueue(MeetingRepository meetingRepository,
                               CaptionSegmentRepository captionSegmentRepository,
                               TransactionTemplate transactionTemplate,
                               MeterRegistry registry,
                               @Value("${edumeet.caption.archive.enabled:true}") boolean enabled) {
        this.meetingRepository = meetingRepository;
        this.captionSegmentRepository = captionSegmentRepository;
        this.transactionTemplate = transactionTemplate;
        this.enabled = enabled;

        Gauge.builder("caption.archive.queued", queue, Collection::size)
                .description("저장 대기 중인 final 자막 수")
                .register(registry);
        Gauge.builder("caption.archive.capacity", this, q -> CAPACITY)
                .description("자막 저장 큐 상한")
                .register(registry);
        this.enqueued = Counter.builder("caption.archive.enqueued")
                .description("저장 큐에 넣은 final 자막 수").register(registry);
        this.dropped = Counter.builder("caption.archive.dropped")
                .description("큐가 가득 차 버린 final 자막 수").register(registry);
        this.persisted = Counter.builder("caption.archive.persisted")
                .description("DB 에 저장한 final 자막 수").register(registry);
        this.duplicates = Counter.builder("caption.archive.duplicates")
                .description("재시도나 중복 전송으로 저장하지 않은 자막 수").register(registry);
    }

    /** 큐에 넣는다. 가득 차면 버리고 false 를 준다. 절대 요청 스레드를 막지 않는다. */
    public boolean offer(Long meetingId, String text, Long sequence, Long spokenAt,
                         Long receivedAt, Long publishedAt) {
        if (!enabled) {
            return false;
        }
        boolean accepted = queue.offer(new Pending(
                meetingId, text, sequence, spokenAt, receivedAt, publishedAt));
        if (accepted) {
            enqueued.increment();
        } else {
            dropped.increment();
        }
        return accepted;
    }

    @Scheduled(fixedDelayString = "${edumeet.caption.archive.flush-interval-ms:1000}")
    public void flush() {
        List<Pending> batch = new ArrayList<>(BATCH_SIZE);
        queue.drainTo(batch, BATCH_SIZE);
        if (batch.isEmpty()) {
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(ignored -> persist(batch));
        } catch (Exception e) {
            log.warn("자막 배치 저장 실패 - {}건 유실. {}", batch.size(), e.toString());
        }
    }

    private void persist(List<Pending> batch) {
        Map<Long, Meeting> meetings = new HashMap<>();
        Map<Long, Set<Long>> existingSequences = existingSequencesByMeeting(batch);
        Map<Long, Set<Long>> seenInBatch = new HashMap<>();
        List<CaptionSegment> rows = new ArrayList<>(batch.size());

        for (Pending p : batch) {
            Meeting meeting = meetings.computeIfAbsent(
                    p.meetingId(), id -> meetingRepository.findById(id).orElse(null));
            if (meeting == null) {
                continue;
            }
            if (isDuplicate(p, existingSequences, seenInBatch)) {
                duplicates.increment();
                continue;
            }
            rows.add(CaptionSegment.finalOf(
                    meeting, p.sequence(), p.spokenAt(), p.receivedAt(), p.publishedAt(), p.text()));
        }

        if (!rows.isEmpty()) {
            captionSegmentRepository.saveAll(rows);
            persisted.increment(rows.size());
        }
    }

    private Map<Long, Set<Long>> existingSequencesByMeeting(List<Pending> batch) {
        Map<Long, Set<Long>> wanted = new HashMap<>();
        for (Pending p : batch) {
            if (p.sequence() != null) {
                wanted.computeIfAbsent(p.meetingId(), ignored -> new HashSet<>()).add(p.sequence());
            }
        }
        Map<Long, Set<Long>> existing = new HashMap<>();
        wanted.forEach((meetingId, sequences) -> existing.put(
                meetingId, captionSegmentRepository.findExistingSequences(meetingId, sequences)));
        return existing;
    }

    private boolean isDuplicate(Pending p,
                                Map<Long, Set<Long>> existingSequences,
                                Map<Long, Set<Long>> seenInBatch) {
        if (p.sequence() == null) {
            return false;
        }
        Set<Long> existing = existingSequences.getOrDefault(p.meetingId(), Set.of());
        Set<Long> seen = seenInBatch.computeIfAbsent(p.meetingId(), ignored -> new HashSet<>());
        return existing.contains(p.sequence()) || !seen.add(p.sequence());
    }

    @PreDestroy
    public void drainOnShutdown() {
        int remaining = queue.size();
        if (remaining > 0) {
            log.info("종료 - 자막 {}건 남아 있어 비운다", remaining);
            while (!queue.isEmpty()) {
                flush();
            }
        }
    }

    /** 테스트와 지표 검증용. */
    public int queuedCount() {
        return queue.size();
    }

    private record Pending(Long meetingId, String text, Long sequence, Long spokenAt,
                           Long receivedAt, Long publishedAt) {}
}
