package com.edu.edumeet.meeting.service;

import com.edu.edumeet.meeting.dto.CaptionBroadcast;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 끊겼다 다시 붙은 사람에게 그 사이 자막을 밀어 주기 위해 최근 것을 들고 있는다. (#165)
 *
 * <h3>왜 필요한가 — 재접속률 100% 와 자막 유실은 같이 난다</h3>
 * 재접속 자체는 문제가 없었다. 구독자 50명을 3초씩 3번 끊었다 붙였더니
 * <b>150/150 성공, p95 292ms</b> 다. 그런데 같은 회차에서
 * <b>9,475건을 놓쳤다</b>(재접속당 63건).
 *
 * <pre>
 *   끊어 둔 3초 x 발행 20/s  =  60건
 *   실측 재접속당 놓친 건수   =  63건
 * </pre>
 *
 * 예상과 실측이 맞으니 <b>일부가 아니라 전부</b>다. STOMP 는 재전송을 안 하므로
 * 다시 붙어도 그 구간은 안 메워진다. <b>접속 수만 세면 이 구멍이 안 보인다.</b>
 * 청각장애 학습자에게 3초 끊김은 자막 63건이 없는 것과 같다.
 *
 * <h3>여기서 모순이 하나 생긴다</h3>
 * {@link CaptionBroadcastQueue} 는 정반대를 한다 —
 * <b>밀린 자막은 버린다.</b> "5초 밀린 자막은 화면의 음성과 안 맞아 없는 것보다 나쁘다"
 * 가 그 근거였다. 그런데 여기서는 지난 자막을 일부러 다시 보낸다.
 *
 * <p><b>둘은 다른 상황이다.</b>
 * <pre>
 *   밀린 자막   사용자는 그동안 다음 말을 이미 들었다  ->  지금 말로 오해한다. 버린다
 *   놓친 자막   사용자는 그동안 아무것도 못 받았다      ->  구멍이다. 메운다
 * </pre>
 *
 * <h3>그래서 두 가지를 지킨다</h3>
 * <ol>
 *   <li><b>{@code finalSegment} 만 들고 있는다.</b> 중간 자막은 지나가면 값이 없다.
 *       버퍼도 작아진다</li>
 *   <li><b>{@code replay=true} 로 표시해서 보낸다.</b> 클라이언트가 이것을 실시간 자막 줄에
 *       띄우면 안 된다. 지나간 구간을 채우는 것이라 <b>전사 영역에 붙여야 한다.</b>
 *       표시가 없으면 받는 쪽이 이 둘을 구분할 방법이 없다</li>
 * </ol>
 *
 * <h3>상한을 둘로 거는 이유</h3>
 * 건수만 걸면 조용한 강의에서 <b>10분 전 자막</b>이 남는다. 시간만 걸면 말이 빠른 구간에서
 * 버퍼가 커진다. <b>둘 중 먼저 걸리는 쪽으로 자른다.</b>
 */
@Component
@Slf4j
public class CaptionReplayBuffer {

    private final Map<Long, Deque<CaptionBroadcast>> byMeeting = new ConcurrentHashMap<>();
    private final int maxItems;
    private final long maxAgeMs;

    public CaptionReplayBuffer(
            MeterRegistry registry,
            @Value("${edumeet.caption.replay.max-items:60}") int maxItems,
            @Value("${edumeet.caption.replay.max-age-ms:60000}") long maxAgeMs) {
        this.maxItems = maxItems;
        this.maxAgeMs = maxAgeMs;
        Gauge.builder("caption.replay.buffered", byMeeting,
                        m -> m.values().stream().mapToInt(d -> { synchronized (d) { return d.size(); } }).sum())
                .description("재접속 복구용으로 들고 있는 자막 수. 방 전체 합")
                .register(registry);
        Gauge.builder("caption.replay.rooms", byMeeting, Map::size)
                .description("복구 버퍼를 가진 방 수. 회의가 끝나면 줄어야 한다")
                .register(registry);
    }

    /** 최종 자막만 들고 있는다. 중간 자막은 지나가면 값이 없다. */
    public void remember(CaptionBroadcast payload) {
        if (payload == null || !Boolean.TRUE.equals(payload.finalSegment())) return;
        Deque<CaptionBroadcast> q = byMeeting.computeIfAbsent(
                payload.meetingId(), k -> new ArrayDeque<>());
        synchronized (q) {
            q.addLast(payload);
            trim(q, payload.publishedAt());
        }
    }

    /**
     * 이 방의 최근 자막을 오래된 것부터 준다.
     *
     * <p>지금은 <b>구독자가 어디까지 받았는지 모른다.</b> 그래서 버퍼 전체를 준다.
     * 이미 받은 것이 섞여 나가는데, {@code sequence} 가 있으니 받는 쪽이 거를 수 있다.
     * 구독 시점에 "마지막으로 받은 번호" 를 헤더로 받아 그 뒤만 주는 것이 다음 단계다.
     */
    public List<CaptionBroadcast> recent(Long meetingId) {
        Deque<CaptionBroadcast> q = byMeeting.get(meetingId);
        if (q == null) return Collections.emptyList();
        synchronized (q) {
            trim(q, System.currentTimeMillis());
            return new ArrayList<>(q);
        }
    }

    /** 회의가 끝나면 버린다. 안 버리면 방이 늘수록 힙에 쌓인다. */
    public void forget(Long meetingId) {
        if (byMeeting.remove(meetingId) != null) {
            log.debug("자막 복구 버퍼를 비웠다. meetingId={}", meetingId);
        }
    }

    /** 건수와 나이 중 먼저 걸리는 쪽으로 자른다. */
    private void trim(Deque<CaptionBroadcast> q, long now) {
        while (q.size() > maxItems) q.pollFirst();
        while (!q.isEmpty()) {
            Long at = q.peekFirst().publishedAt();
            if (at != null && now - at > maxAgeMs) q.pollFirst();
            else break;
        }
    }
}
