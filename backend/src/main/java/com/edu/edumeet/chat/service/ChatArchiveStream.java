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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetAddress;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 다시보기 채팅의 저장 대기열을 Redis Stream 으로 둔다. (#201)
 *
 * <h3>왜 메모리 큐를 버리나</h3>
 *
 * <p>B8 에서 쟀다. {@code kill -9} 로 죽이면 메모리 큐에 있던 20~30건이 사라지는데
 * <b>지표에는 아무것도 안 남는다</b> — 버린 것이 아니라 지표를 남길 주체가 사라졌기 때문이다.
 * 상한이 2,000 이므로 최악 2,000건이 흔적 없이 사라진다.
 *
 * <pre>
 *   발행 → 브로드캐스트 (그대로, 먼저)
 *        → XADD chat:archive * {...}
 *   소비 → XREADGROUP GROUP archiver &lt;consumer&gt; COUNT 200 BLOCK 1000
 *        → DB INSERT (message_uid 유니크 - 중복은 건너뛴다)
 *        → 커밋 뒤 XACK
 *   주기 → XPENDING 으로 min-idle 90초 넘은 것을 XCLAIM : 죽은 소비자가 놓친 것을 산 소비자가 가져간다
 *        → 5회 넘게 실패한 항목은 chat:archive:dead 로 격리하고 ACK (배치를 막지 않게)
 *        → XTRIM MINID : **ACK 가 끝난 항목만** 지운다
 * </pre>
 *
 * <h3>버린 대안</h3>
 *
 * <pre>
 *   로컬 디스크 WAL   블루·그린 슬롯이 서로의 파일을 못 읽는다. 죽은 슬롯의 WAL 을
 *                    누가 재생하나가 풀리지 않는다
 *   Kafka · NATS      CLAUDE.md §6 에서 이미 기각(지연·운영 부담 / 보장 수준 동일)
 *   메모리 재시도      #43 의 OOM 이 돌아온다
 * </pre>
 *
 * <h3>최소 한 번 전달 → 중복이 가능하다</h3>
 *
 * <p>DB 에 넣고 ACK 하기 전에 죽으면 다른 소비자가 같은 항목을 다시 가져간다.
 * {@code message_uid} 에 유니크 제약(V10)을 걸어 <b>DB 가 중복을 막는다.</b>
 * "넣기 전에 조회" 만으로는 조회와 삽입 사이에 창이 남는다.
 *
 * <h3>DB 저장에 실패하면 ACK 하지 않는다</h3>
 *
 * <p>여기가 메모리 큐와 가장 다른 점이다. 실패한 항목은 <b>pending 으로 남아</b>
 * {@code claimStale} 이 다시 가져간다 — 버리지 않는다. 대신 같은 항목이 계속 실패하면
 * pending 이 쌓이므로 {@code chat.archive.stream.pending} 으로 보인다.
 *
 * <h3>Redis 가 죽으면 발행은 계속된다</h3>
 *
 * <p>{@link #publish} 는 실패해도 던지지 않고 {@code false} 를 준다. 호출자가 기존 메모리 큐로
 * 떨어뜨린다. 방송이 멈추는 것보다 다시보기가 조금 비는 게 낫다는 기존 원칙 그대로다. (#61)
 */
@Component
@Slf4j
public class ChatArchiveStream {

    static final String STREAM_KEY = "chat:archive";
    static final String GROUP = "archiver";
    /** 영원히 실패하는 항목이 가는 곳. 사람이 보고 판단한다 - 여기 쌓여도 배치를 막지 않는다. */
    static final String DEAD_KEY = "chat:archive:dead";

    /** 한 번에 읽는 건수. DB 배치 크기와 맞춘다. */
    private static final int BATCH = 200;

    /**
     * 비상 상한. <b>정상 경로에서는 절대 여기까지 오지 않는다.</b>
     *
     * <p>트리밍은 ACK 가 끝난 항목만 지운다(아래 {@link #trimStream}). 그런데 그것만으로는
     * Redis 메모리를 지키는 것이 <b>경보</b>뿐이라, 경보를 아무도 안 보는 동안 발행이
     * 계속되면 메모리가 무한히 큰다. 그 마지막 밸브가 이 값이다.
     *
     * <p>여기서 지워지는 수는 {@code chat.archive.stream.trimmed} 로 센다 -
     * 조용히 지우면 그게 B8 의 유실이다.
     */
    private static final long EMERGENCY_MAX_LEN = 1_000_000L;   // 기본값. 설정으로 바꿀 수 있다

    /**
     * 한 주기에 읽는 최대 건수.
     *
     * <p>폴링 주기가 처리량 상한이 되지 않게 반복해서 읽되, 무한히 붙잡고 있지는 않는다.
     * 여기까지 읽었는데도 남아 있으면 다음 주기가 이어서 읽는다.
     */
    private static final int MAX_PER_CYCLE = 20_000;

    private static final String F_UID = "uid";
    private static final String F_MEETING = "meetingId";
    private static final String F_SENDER = "sender";
    private static final String F_CONTENT = "content";
    private static final String F_OFFSET = "offsetMillis";
    private static final String F_SENT_AT = "sentAt";
    private static final String F_DEAD_REASON = "deadReason";
    private static final String F_DEAD_DELIVERIES = "deliveries";

    private final StringRedisTemplate redis;
    private final ChatMessageRepository chatMessageRepository;
    private final MeetingRepository meetingRepository;
    private final TransactionTemplate transactionTemplate;

    private final Counter enqueued;
    private final Counter persisted;
    private final Counter persistFailed;
    private final Counter duplicates;
    private final Counter claimed;
    private final Counter fallback;
    private final Counter dead;
    private final Counter discarded;
    private final Counter emergencyTrimmed;

    private final String consumerName;
    private final long claimMinIdleMs;
    private final int maxDeliveries;
    private final boolean enabled;
    /** Redis 실패 뒤 XADD 를 건너뛰는 시간(ms). 0 이면 끈다. */
    private final long breakerMs;
    /** 비상 상한. 넘으면 지우고 지운 수를 센다. 시험에서는 작게 잡아 그 경로를 확인한다. */
    private final long emergencyMaxLen;
    /** 이만큼 **연속으로** 실패해야 창을 연다. 한 번 삐끗한 것을 장애로 보지 않는다. */
    private final int breakerFailures;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();

    /** 지표용. 1 = 차단 창이 열려 있다. */
    private final AtomicLong breakerOpen = new AtomicLong();
    /** 창이 닫히는 시각(ms). 0 = 닫혀 있다. */
    private final AtomicLong skipUntilMillis = new AtomicLong();

    /** 스크레이프마다 Redis 를 부르지 않도록 주기 갱신 값으로 낸다. */
    private final AtomicLong pendingCount = new AtomicLong();
    private final AtomicLong streamLength = new AtomicLong();
    private final AtomicLong lagMillis = new AtomicLong();

    public ChatArchiveStream(StringRedisTemplate redis,
                             ChatMessageRepository chatMessageRepository,
                             MeetingRepository meetingRepository,
                             org.springframework.transaction.PlatformTransactionManager transactionManager,
                             MeterRegistry registry,
                             @Value("${edumeet.chat.archive.enabled:true}") boolean enabled,
                             @Value("${edumeet.chat.archive.claim-min-idle-ms:90000}") long claimMinIdleMs,
                             @Value("${edumeet.chat.archive.stream.max-deliveries:5}") int maxDeliveries,
                             @Value("${edumeet.chat.archive.stream.breaker-ms:5000}") long breakerMs,
                             @Value("${edumeet.chat.archive.stream.breaker-failures:3}") int breakerFailures,
                             @Value("${edumeet.chat.archive.stream.emergency-max-len:1000000}") long emergencyMaxLen) {
        this.redis = redis;
        this.chatMessageRepository = chatMessageRepository;
        this.meetingRepository = meetingRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.enabled = enabled;
        this.claimMinIdleMs = claimMinIdleMs;
        this.maxDeliveries = maxDeliveries;
        this.breakerMs = breakerMs;
        this.breakerFailures = breakerFailures;
        this.emergencyMaxLen = emergencyMaxLen;
        // 소비자 이름은 인스턴스마다 달라야 한다. 죽은 소비자의 이름을 재사용하면
        // 그 이름으로 남은 pending 이 "내 것" 으로 보여 클레임 판단이 흐려진다.
        this.consumerName = "archiver-" + hostName() + "-" + UUID.randomUUID().toString().substring(0, 8);

        // ★ 기존 이름(chat.archive.*)의 의미를 유지한다. 경보(ChatArchiveLosing·ChatArchiveBacklog)가
        //   그 이름을 본다. 같은 이름으로 등록하면 Micrometer 가 같은 계량기를 돌려주므로
        //   두 경로(스트림·메모리)의 값이 한 줄로 합쳐진다.
        this.enqueued = Counter.builder("chat.archive.enqueued")
                .description("큐에 넣은 수").register(registry);
        this.persisted = Counter.builder("chat.archive.persisted")
                .description("실제로 저장한 수").register(registry);
        this.persistFailed = Counter.builder("chat.archive.persist.failed")
                .description("DB 저장 실패로 버린 수. dropped 와 원인이 다르다 - 이쪽은 DB 문제")
                .register(registry);

        this.claimed = Counter.builder("chat.archive.stream.claimed")
                .description("죽은 소비자가 놓친 것을 클레임해 저장한 수").register(registry);
        this.duplicates = Counter.builder("chat.archive.stream.duplicates")
                .description("이미 저장된 항목이라 건너뛴 수. 최소 한 번 전달의 대가다")
                .register(registry);
        this.fallback = Counter.builder("chat.archive.stream.fallback")
                .description("Redis 실패로 메모리 큐로 떨어진 수. 0 이 아니면 Redis 를 봐야 한다")
                .register(registry);
        this.dead = Counter.builder("chat.archive.stream.dead")
                .description("여러 번 실패해 격리한 항목 수. 0 이 아니면 사람이 봐야 한다")
                .register(registry);
        this.discarded = Counter.builder("chat.archive.stream.discarded")
                .description("저장할 수 없어 버린 항목 수(회의가 지워졌거나 행이 제약을 위반)").register(registry);
        this.emergencyTrimmed = Counter.builder("chat.archive.stream.trimmed")
                .description("비상 상한을 넘겨 지운 수. 지운 것은 저장되지 않는다 - 0 이어야 한다")
                .register(registry);

        Gauge.builder("chat.archive.stream.pending", pendingCount, AtomicLong::get)
                .description("ACK 못 한 항목 수(XPENDING). 계속 늘면 저장이 못 따라가고 있다")
                .register(registry);
        Gauge.builder("chat.archive.stream.lag.ms", lagMillis, AtomicLong::get)
                .description("소비가 뒤처진 시간(ms). 소비가 발행을 못 따라가면 여기가 커진다")
                .register(registry);
        Gauge.builder("chat.archive.stream.breaker.open", breakerOpen, AtomicLong::get)
                .description("Redis 발행 실패로 XADD 를 건너뛰는 중인가(1/0). 열려 있으면 발행은 메모리 큐로 간다")
                .register(registry);
        Gauge.builder("chat.archive.stream.length", streamLength, AtomicLong::get)
                .description("스트림 길이(XLEN). ACK 가 끝난 항목만 지우므로 소비가 밀리면 큰다")
                .register(registry);
    }

    /**
     * 스트림에 넣는다. <b>실패해도 던지지 않는다.</b>
     *
     * @return 넣었으면 true. false 면 호출자가 메모리 큐로 떨어뜨린다
     */
    public boolean publish(Long meetingId, String senderEmail, String content, Long offsetMillis) {
        if (!enabled) {
            return false;
        }
        // ★ 차단 창. 실패한 뒤 몇 초 동안은 Redis 를 부르지 않고 바로 메모리 큐로 떨어진다. (검토 8)
        //
        //   근거는 측정이다. Redis 를 멈추고 100건/초로 발행했더니
        //   **발행 p50 1.83초 · p99 2.14초** 였다. 연결 거부가 즉시 오는 조건(가장 좋은 경우)인데도
        //   그랬다 - 실패를 기다리는 시간이 발행 요청에 그대로 붙는다.
        //   `spring.data.redis.timeout` 이 2초라 그 2초가 상한이다.
        //   열려 있는 동안에는 그 2초조차 쓰지 않는다.
        if (breakerMs > 0 && System.currentTimeMillis() < skipUntilMillis.get()) {
            return false;
        }

        Map<String, String> body = new HashMap<>();
        body.put(F_UID, UUID.randomUUID().toString());
        body.put(F_MEETING, String.valueOf(meetingId));
        body.put(F_SENDER, senderEmail);
        body.put(F_CONTENT, content);
        body.put(F_OFFSET, offsetMillis == null ? "" : String.valueOf(offsetMillis));
        body.put(F_SENT_AT, String.valueOf(System.currentTimeMillis()));

        try {
            RecordId id = ops().add(STREAM_KEY, body);
            if (id == null) {
                return false;
            }
            enqueued.increment();
            consecutiveFailures.set(0);
            closeBreaker();
            return true;
        } catch (Exception e) {
            // Redis 가 죽었다. 발행을 막지 않는다 - 방송이 멈추는 것보다 낫다.
            if (consecutiveFailures.incrementAndGet() >= breakerFailures) {
                openBreaker();
            }
            return false;
        }
    }

    /**
     * 차단 창을 연다. <b>연속 실패가 기준을 넘었을 때만</b> 부른다.
     *
     * <p>한 번의 일시적 실패로 창을 열면 그 뒤 5초가 통째로 메모리 큐로 가버린다 -
     * 메모리 큐는 프로세스가 죽으면 사라지는 경로라서 그 대가가 작지 않다.
     * 장애면 실패가 연속되므로 몇 번 뒤에 열려도 늦지 않다.
     */
    private void openBreaker() {
        if (breakerMs <= 0) {
            return;
        }
        // ★ 동시에 열 때 **만료 시각이 짧아지면 안 된다.** (검토 2)
        //   getAndSet 은 마지막에 쓴 값이 이긴다 - 요청 스레드 20개가 동시에 실패하면
        //   먼저 계산한(더 늦은) 값이 나중 값에 덮여 창이 오히려 짧아질 수 있다.
        //   더 큰 값을 남긴다 - 창은 "지금부터 N초" 가 아니라 "적어도 이 시각까지" 다.
        //   벽시계를 쓴다. nanoTime 은 부호가 임의라(음수가 될 수 있다) max 비교가 뒤집힐 수 있다 -
        //   실제로 그 때문에 창이 열리지 않아 시험이 잡았다.
        long until = System.currentTimeMillis() + breakerMs;
        long previous = skipUntilMillis.accumulateAndGet(until, Math::max);
        breakerOpen.set(1);
        if (previous == 0) {
            log.warn("Redis 발행 실패 - {}ms 동안 XADD 를 건너뛰고 메모리 큐로 바로 떨어진다", breakerMs);
        }
    }

    private void closeBreaker() {
        if (skipUntilMillis.getAndSet(0) != 0) {
            breakerOpen.set(0);
            consecutiveFailures.set(0);
            log.info("Redis 발행이 다시 성공했다 - 차단 창을 닫는다");
        }
    }

    /** Redis 실패로 메모리 큐에 떨어졌을 때 센다. */
    public void recordFallback() {
        fallback.increment();
    }

    /**
     * 주기적으로 스트림을 비운다.
     *
     * <p><b>한 주기에 배치 하나만 읽으면 처리량 상한이 배치 크기 ÷ 주기가 된다</b> -
     * 200건 ÷ 1초 = 초당 200건. 그러면 저장 속도를 DB 가 아니라 <b>폴링 주기가</b> 정한다.
     * 그 상한은 DB 가 감당할 수 있는 양과 아무 관계가 없다 - 설계의 부작용일 뿐이다.
     * 그래서 대기열이 빌 때까지 반복해서 읽는다(상한은 DB 가 정한다).
     *
     * <p>다 읽고 나면 한 번 더 읽는데, 그때만 {@code BLOCK 1000} 만큼 기다린다.
     * Redis 가 죽어 있으면 예외를 삼키고 다음 주기에 다시 본다.
     */
    @Scheduled(fixedDelayString = "${edumeet.chat.archive.stream.poll-ms:1000}")
    public void poll() {
        if (!enabled) {
            return;
        }
        try {
            ensureGroup();
            // ★ 살아 있는지 확인하는 일은 **발행 경로가 아니라 여기서** 한다. (검토 8)
            //   차단 창을 열어 두고 발행 때마다 다시 시도하면, 창이 닫히는 순간의 그 한 건이
            //   명령 타임아웃(2초)만큼 느려져 p99 가 그대로 남는다(측정: p99 2.0초).
            //   폴링은 1초마다 어차피 Redis 를 부르므로 여기서 닫으면 된다 -
            //   사용자 경로는 장애 하나에 한 번만 느리다.
            closeBreaker();
            int read = 0;
            while (read < MAX_PER_CYCLE) {
                List<MapRecord<String, Object, Object>> batch = readNew();
                if (batch.isEmpty()) {
                    break;
                }
                read += batch.size();
                persistAndAck(batch);
            }
            persistAndAck(claimStale());
            trimStream();
            refreshGauges();
        } catch (Exception e) {
            // 여기서 창을 열지 않는다. 이 블록은 DB 저장 실패도 잡는다 -
            // DB 문제를 Redis 장애로 오해하면 발행이 쓸데없이 폴백으로 간다.
            // Redis 가 죽었으면 발행이 연속 실패하며 스스로 연다.
            log.warn("채팅 저장 스트림 처리 실패 - 다음 주기에 다시 본다. {}", e.toString());
        }
    }

    /**
     * <b>ACK 가 끝난 항목만 지운다.</b> 읽지 않았거나 ACK 전인 항목은 지우지 않는다.
     *
     * <p>경계는 {@code min(그룹이 마지막으로 배달한 id, 가장 오래된 pending id)} 다.
     * 그 앞은 이미 누군가에게 배달됐고(그래서 아직 ACK 안 된 것이 있으면 그게 더 앞이다)
     * 전부 ACK 가 끝난 항목이다. {@code XTRIM MINID <경계>} 로 그 앞만 지운다.
     *
     * <p><b>★ MAXLEN 으로 자르면 안 된다. (검토 6)</b> MAXLEN 은 ACK 여부를 보지 않는다 -
     * 아직 읽지 않은 항목과 처리 중인 pending 을 오래된 것부터 지운다.
     * <b>유실을 없애려던 장치가 유실 경로가 된다.</b> B9r 사다리에서 스트림이 191,335 까지
     * 자랐고, 그 구간에서 100,000 을 넘긴 만큼이 MAXLEN 으로 지워지고 있었다.
     *
     * <p><b>그럼 메모리는 무엇이 지키나.</b> 지키는 방법을 삭제에서 <b>경보</b>로 옮겼다 -
     * 소비가 밀리면 {@code ChatArchiveStreamBehind}(1분)가 울리고, 더 밀리면
     * {@code ChatArchiveStreamBacklog} 가 울린다. 그때 사람이 소비를 늘린다.
     * 지워서 지키면 그 순간 다시보기 채팅이 사라진다 - 무엇을 지킬지의 문제다.
     * 마지막 밸브는 {@link #trimEmergency} 하나만 남겼고, 거기서 지운 수는 지표로 센다.
     */
    void trimStream() {
        try {
            String floor = trimFloor();
            if (floor != null) {
                Long removed = redis.execute((org.springframework.data.redis.core.RedisCallback<Long>) connection ->
                        (Long) connection.execute("XTRIM",
                                utf8(STREAM_KEY), utf8("MINID"), utf8(floor)));
                if (removed != null && removed > 0) {
                    log.debug("ACK 가 끝난 항목 {}건을 스트림에서 지웠다 (경계 {})", removed, floor);
                }
            }
        } catch (Exception e) {
            log.warn("ACK 경계 트리밍 실패: {}", e.toString());
        }
        // ★ 비상 밸브는 **따로** 돈다. 그룹이 없거나 위에서 예외가 나도 메모리 보호는 살아 있어야 한다.
        //   한 try 에 묶어 두면 경계 계산이 던지는 순간 밸브까지 멈춘다. (검토 1)
        try {
            trimEmergency();
        } catch (Exception e) {
            log.warn("비상 트리밍 실패: {}", e.toString());
        }
    }

    /**
     * 지워도 되는 경계 id. 여기보다 <b>오래된</b> 항목만 지울 수 있다.
     *
     * <p><b>★ 그룹이 없으면 {@code XPENDING} 을 부르지 않는다. (검토 1)</b>
     * 그룹이 없는데 부르면 {@code NOGROUP} 오류가 난다 - 그리고 그 오류가 밖으로 나가면
     * 이 메서드를 부른 {@link #trimStream} 의 try 가 통째로 끝나 <b>비상 트리밍까지 멈춘다.</b>
     * 그룹이 없다는 것은 아직 아무것도 배달되지 않았다는 뜻이라 지울 것도 없다 - null 로 건너뛴다.
     *
     * @return 경계 id. 그룹이 없거나 배달한 적이 없으면 null(아무것도 지우지 않는다)
     */
    String trimFloor() {
        String delivered = null;
        boolean groupExists = false;
        var groups = ops().groups(STREAM_KEY);
        if (groups != null) {
            for (var group : groups) {
                if (GROUP.equals(group.groupName())) {
                    delivered = group.lastDeliveredId();
                    groupExists = true;
                }
            }
        }
        if (!groupExists) {
            return null;
        }

        String oldestPending = null;
        PendingMessages pending = ops().pending(STREAM_KEY, GROUP,
                org.springframework.data.domain.Range.unbounded(), 1);
        if (pending != null && !pending.isEmpty()) {
            oldestPending = pending.get(0).getId().getValue();
        }
        return smallerId(delivered, oldestPending);
    }

    /**
     * 비상 밸브. 경보를 아무도 안 보는 동안에도 Redis 메모리가 무한히 크지 않게 하는 마지막 장치다.
     *
     * <p>여기서 지워지는 것은 <b>아직 저장하지 못한 항목일 수 있다.</b> 그래서 조용히 지우지 않고
     * 센다({@code chat.archive.stream.trimmed}) - 이 값이 0 이 아니면 이미 유실이 났다는 뜻이다.
     */
    void trimEmergency() {
        Long len = ops().size(STREAM_KEY);
        if (len == null || len <= emergencyMaxLen) {
            return;
        }
        Long removed = ops().trim(STREAM_KEY, emergencyMaxLen, false);
        if (removed != null && removed > 0) {
            emergencyTrimmed.increment(removed);
            log.error("비상 상한({})을 넘어 {}건을 지웠다 - 저장하지 못한 항목이 섞여 있을 수 있다",
                    emergencyMaxLen, removed);
        }
    }

    private static String smallerId(String a, String b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return compareIds(a, b) <= 0 ? a : b;
    }

    /** 항목 id 는 {@code <ms>-<seq>} 다. 문자열로 비교하면 자릿수가 달라질 때 어긋난다. */
    private static int compareIds(String a, String b) {
        long[] x = idParts(a);
        long[] y = idParts(b);
        return x[0] != y[0] ? Long.compare(x[0], y[0]) : Long.compare(x[1], y[1]);
    }

    private static long[] idParts(String id) {
        int dash = id.indexOf('-');
        try {
            return new long[]{
                    Long.parseLong(dash < 0 ? id : id.substring(0, dash)),
                    dash < 0 ? 0 : Long.parseLong(id.substring(dash + 1))};
        } catch (NumberFormatException e) {
            return new long[]{0, 0};
        }
    }

    private static byte[] utf8(String value) {
        return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 그룹이 없으면 만든다. 이미 있으면 아무 일도 하지 않는다. */
    void ensureGroup() {
        try {
            ops().createGroup(STREAM_KEY, ReadOffset.from("0"), GROUP);
        } catch (RedisSystemException e) {
            // BUSYGROUP - 이미 있다. 정상이다.
        }
    }

    /** 새 항목을 읽는다. ACK 는 저장이 끝난 뒤에 한다. */
    List<MapRecord<String, Object, Object>> readNew() {
        List<MapRecord<String, Object, Object>> records = ops().read(
                Consumer.from(GROUP, consumerName),
                StreamReadOptions.empty().count(BATCH).block(Duration.ofMillis(1000)),
                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()));
        return records == null ? List.of() : records;
    }

    /**
     * 오래 매달린 항목을 가져온다. <b>죽은 소비자가 남긴 것을 살아 있는 소비자가 이어받는다.</b>
     *
     * <p>이것이 {@code kill -9} 로 죽어도 유실되지 않는 이유다 — 항목이 사라지는 게 아니라
     * 다른 소비자의 pending 으로 남아 있다가 여기로 온다.
     */
    List<MapRecord<String, Object, Object>> claimStale() {
        PendingMessages pending = ops().pending(STREAM_KEY, GROUP,
                org.springframework.data.domain.Range.unbounded(), BATCH);
        if (pending == null || pending.isEmpty()) {
            return List.of();
        }

        List<RecordId> stale = new ArrayList<>();
        List<org.springframework.data.redis.connection.stream.PendingMessage> poison = new ArrayList<>();
        for (org.springframework.data.redis.connection.stream.PendingMessage message : pending) {
            if (message.getTotalDeliveryCount() >= maxDeliveries) {
                // ★ 계속 실패하는 항목은 여기서 격리한다. (검토 2)
                //   안 그러면 그 항목이 배치마다 다시 실패해 **배치 전체를 영원히 막는다** -
                //   뒤에 쌓인 정상 항목이 하나도 저장되지 않는다.
                poison.add(message);
                continue;
            }
            if (message.getElapsedTimeSinceLastDelivery() != null
                    && message.getElapsedTimeSinceLastDelivery().toMillis() >= claimMinIdleMs) {
                stale.add(message.getId());
            }
        }
        quarantine(poison);

        if (stale.isEmpty()) {
            return List.of();
        }

        List<MapRecord<String, Object, Object>> claimedRecords = ops().claim(STREAM_KEY, GROUP,
                consumerName, Duration.ofMillis(claimMinIdleMs), stale.toArray(new RecordId[0]));
        return claimedRecords == null ? List.of() : claimedRecords;
    }

    /**
     * 여러 번 실패한 항목을 죽은 편지함({@link #DEAD_KEY})으로 옮기고 ACK 한다.
     *
     * <p>본문을 그대로 복사한다 - 격리한 것이 무엇이었는지 나중에 볼 수 있어야 한다.
     * 옮기지 못하면 ACK 하지 않는다(다음 주기에 다시 시도).
     *
     * @return 옮긴 수
     */
    int quarantine(List<org.springframework.data.redis.connection.stream.PendingMessage> poison) {
        int moved = 0;
        for (org.springframework.data.redis.connection.stream.PendingMessage message : poison) {
            try {
                List<MapRecord<String, Object, Object>> found = ops().range(STREAM_KEY,
                        org.springframework.data.domain.Range.just(message.getId().getValue()));
                if (found == null || found.isEmpty()) {
                    // 본문이 스트림에 없다(이미 지워졌거나 비상 상한으로 잘려 나갔다).
                    // 옮길 것이 없으므로 ACK 만 하고 센다 - 여기서 조용히 넘어가면 유실을 못 본다.
                    ops().acknowledge(STREAM_KEY, GROUP, message.getId());
                    dead.increment();
                    moved += 1;
                    log.warn("격리할 항목의 본문이 스트림에 없다: id={}", message.getId());
                    continue;
                }
                Map<String, String> body = new HashMap<>();
                found.get(0).getValue().forEach((k, v) -> body.put(String.valueOf(k), String.valueOf(v)));
                body.put(F_DEAD_REASON, "deliveries=" + message.getTotalDeliveryCount()
                        + ", consumer=" + message.getConsumerName());
                body.put(F_DEAD_DELIVERIES, String.valueOf(message.getTotalDeliveryCount()));

                // ★ 격리 스트림의 항목 id 를 **원본과 같게** 넣는다. (검토 3)
                //   복사(XADD)와 ACK 는 다른 명령이라 그 사이에 죽으면 같은 항목을 두 번 옮긴다.
                //   id 를 같게 하면 Redis 가 두 번째를 거부하므로 그 자체가 멱등 표시가 된다.
                //   (자동 id 로 넣으면 재시도마다 격리본이 늘어난다)
                RecordId sourceId = message.getId();
                try {
                    ops().add(org.springframework.data.redis.connection.stream.StreamRecords
                            .mapBacked(body)
                            .withStreamKey(DEAD_KEY)
                            .withId(sourceId));
                } catch (Exception copyError) {
                    if (!isAlreadyQuarantined(sourceId, copyError)) {
                        throw copyError;
                    }
                    // 이미 옮겨져 있다 - 복사와 ACK 사이에 죽었던 것을 다시 도는 중이다.
                    log.info("이미 격리된 항목이다 - ACK 만 한다: id={}", sourceId);
                }

                ops().acknowledge(STREAM_KEY, GROUP, message.getId());
                dead.increment();
                moved += 1;
                // ★ ERROR 로 남긴다. 이건 "설계대로 동작했다" 가 아니라 "데이터가 빠졌다" 는 신호다.
                log.error("채팅 저장이 반복 실패해 격리했다 - 다시보기에서 빠진다: id={} deliveries={} consumer={}",
                        message.getId(), message.getTotalDeliveryCount(), message.getConsumerName());
            } catch (Exception e) {
                log.warn("죽은 편지함으로 옮기지 못했다 - 다음 주기에 다시 시도한다: id={} {}",
                        message.getId(), e.toString());
            }
        }
        return moved;
    }

    /**
     * 읽은 항목을 저장하고 ACK 한다.
     *
     * <p><b>저장에 실패하면 ACK 하지 않는다.</b> 항목은 pending 으로 남아 다음 클레임에서
     * 다시 시도된다 — 메모리 큐가 버리던 자리다.
     *
     * @return 저장한 건수
     */
    int persistAndAck(List<MapRecord<String, Object, Object>> records) {
        if (records == null || records.isEmpty()) {
            return 0;
        }

        try {
            Set<String> uids = new LinkedHashSet<>();
            for (MapRecord<String, Object, Object> record : records) {
                String uid = value(record, F_UID);
                if (uid != null && !uid.isBlank()) {
                    uids.add(uid);
                }
            }
            Set<String> existing = uids.isEmpty()
                    ? Set.of()
                    : new HashSet<>(chatMessageRepository.findMessageUidsIn(uids));

            Built built = transactionTemplate.execute(status -> buildRows(records, existing));
            if (built == null) {
                built = new Built(List.of(), 0, 0);
            }
            SaveOutcome outcome = save(built.rows());

            persisted.increment(outcome.saved());
            duplicates.increment(built.duplicates() + outcome.duplicates());
            discarded.increment(built.discarded() + outcome.discarded());

            // 저장이 커밋된 뒤에만 ACK 한다. 순서가 뒤바뀌면 죽었을 때 유실된다.
            RecordId[] ids = records.stream().map(MapRecord::getId).toArray(RecordId[]::new);
            ops().acknowledge(STREAM_KEY, GROUP, ids);
            claimed.increment(records.size());
            return outcome.saved();
        } catch (Exception e) {
            // ★ ACK 하지 않는다. pending 으로 남아 다음 클레임에서 다시 시도된다.
            persistFailed.increment(records.size());
            log.warn("채팅 저장 스트림 배치 실패 - {}건을 ACK 하지 않고 남긴다. {}",
                    records.size(), e.toString());
            return 0;
        }
    }

    /** 배치에서 저장할 행을 만든다. 버린 것(회의 없음·uid 없음)은 세어서 돌려준다. */
    private Built buildRows(List<MapRecord<String, Object, Object>> records, Set<String> existing) {
        Map<Long, Meeting> meetings = new HashMap<>();
        List<ChatMessage> rows = new ArrayList<>(records.size());
        Set<String> added = new HashSet<>();
        int duplicates = 0;
        int discarded = 0;

        for (MapRecord<String, Object, Object> record : records) {
            String uid = value(record, F_UID);
            if (uid == null || uid.isBlank()) {
                discarded += 1;   // uid 가 없으면 중복을 막을 수 없다 - 저장하지 않는다
                log.warn("uid 없는 항목을 버린다: id={}", record.getId());
                continue;
            }
            if (existing.contains(uid) || !added.add(uid)) {
                duplicates += 1;   // 이미 저장됐거나 이 배치 안에서 중복이다
                continue;
            }
            Long meetingId = longValue(record, F_MEETING);
            if (meetingId == null) {
                discarded += 1;
                continue;
            }
            Meeting meeting = meetings.computeIfAbsent(meetingId,
                    id -> meetingRepository.findById(id).orElse(null));
            if (meeting == null) {
                // 회의가 지워졌다. 채팅만 남길 이유가 없다. 예전에는 조용히 버렸는데,
                // 버린 것도 세야 "다시보기가 왜 비었나" 에 답할 수 있다. (검토 2)
                discarded += 1;
                log.warn("지워진 회의의 채팅을 버린다: meetingId={} id={}", meetingId, record.getId());
                continue;
            }
            rows.add(ChatMessage.of(meeting, value(record, F_SENDER), value(record, F_CONTENT),
                    longValue(record, F_OFFSET), uid, sentAt(record)));
        }
        return new Built(rows, duplicates, discarded);
    }

    /**
     * 행을 저장한다. <b>한 건의 유니크 위반이 배치를 실패시키지 않게</b> 두 단계로 넣는다.
     *
     * <p>먼저 묶어서 넣는다. 유니크 위반이 나면 <b>행 단위로 다시 넣는다</b> - 각 행이
     * 자기 트랜잭션이라 한 행이 실패해도 나머지는 저장된다. (검토 4)
     *
     * <p>왜 이게 필요한가: 사전 조회로 중복을 걸러도 <b>조회와 삽입 사이에 창</b>이 남는다.
     * 두 소비자가 동시에 조회하면 둘 다 없다고 보고 둘 다 넣는다. 그때 한쪽의 유니크 위반이
     * 배치 전체를 롤백시키면 <b>그 배치의 정상 항목 전부가 저장되지 않고</b> pending 으로 돌아간다 -
     * 재시도되지만 그동안 뒤가 밀린다.
     */
    SaveOutcome save(List<ChatMessage> rows) {
        if (rows.isEmpty()) {
            return new SaveOutcome(0, 0, 0);
        }
        try {
            transactionTemplate.executeWithoutResult(status -> chatMessageRepository.saveAll(rows));
            return new SaveOutcome(rows.size(), 0, 0);
        } catch (DataIntegrityViolationException e) {
            log.info("배치 저장이 제약을 위반했다 - 행 단위로 다시 넣는다 ({}행): {}",
                    rows.size(), e.getMostSpecificCause().getMessage());
            return saveRowByRow(rows);
        }
    }

    /** 행마다 따로 넣는다. 중복은 건너뛰고, 넣을 수 없는 행은 버리고 센다. */
    SaveOutcome saveRowByRow(List<ChatMessage> rows) {
        int saved = 0;
        int duplicates = 0;
        int discarded = 0;
        for (ChatMessage row : rows) {
            try {
                transactionTemplate.executeWithoutResult(status -> chatMessageRepository.saveAndFlush(row));
                saved += 1;
            } catch (DataIntegrityViolationException rowError) {
                if (isDuplicateKey(rowError)) {
                    duplicates += 1;   // 다른 소비자가 먼저 넣었다. 이건 정상적인 경합이다
                } else {
                    // 제약을 위반하는 행(길이 초과 등)은 영원히 실패한다. 버리고 센다 -
                    // 배치를 통째로 실패시켜 뒤를 막으면 안 된다. (검토 2)
                    discarded += 1;
                    log.warn("저장할 수 없는 행을 버린다: uid={} {}",
                            row.getMessageUid(), rowError.getMostSpecificCause().getMessage());
                }
            }
        }
        return new SaveOutcome(saved, duplicates, discarded);
    }

    /**
     * 그 XADD 오류가 "이미 격리돼 있다" 인가.
     *
     * <p>두 가지를 본다.
     * <ol>
     *   <li>이미 <b>같은 id 가 죽은 편지함에 있는가</b> - 있으면 확실히 옮겨진 것이다</li>
     *   <li>없는데 오류가 "id 가 너무 작다" 라면 <b>순서 문제</b>다 -
     *       더 큰 id 가 먼저 들어가 있으면 나중에 온 작은 id 는 거부된다.
     *       이건 "이미 옮겼다" 가 아니므로 ACK 하면 안 된다(그러면 잃는다). 다음 주기에 다시 본다.</li>
     * </ol>
     * 이 검사가 없으면 순서가 뒤집힌 항목을 "이미 격리됨" 으로 오해해 <b>조용히 버린다.</b>
     */
    private boolean isAlreadyQuarantined(RecordId sourceId, Exception copyError) {
        try {
            var existing = ops().range(DEAD_KEY, org.springframework.data.domain.Range.just(sourceId.getValue()));
            if (existing != null && !existing.isEmpty()) {
                return true;
            }
        } catch (Exception e) {
            log.debug("죽은 편지함을 확인하지 못했다: {}", e.toString());
        }
        log.warn("격리본을 넣지 못했는데 이미 있는 것도 아니다 - ACK 하지 않고 다음 주기에 다시 시도한다: id={} {}",
                sourceId, copyError.toString());
        return false;
    }

    /** 유니크 위반인가. 그 밖의 제약 위반(길이 초과 등)은 정상적인 재시도로 풀리지 않는다. */
    private static boolean isDuplicateKey(DataIntegrityViolationException e) {
        String message = String.valueOf(e.getMostSpecificCause().getMessage()).toLowerCase();
        return message.contains("duplicate") || message.contains("unique");
    }

    /** 배치에서 만든 저장 대상. 버린 것도 센다. */
    private record Built(List<ChatMessage> rows, int duplicates, int discarded) {}

    /** 저장 결과. 유니크 위반으로 행 단위 재시도까지 간 경우를 구분한다. */
    record SaveOutcome(int saved, int duplicates, int discarded) {}

    /** 스크레이프가 Redis 를 기다리지 않도록 주기적으로만 갱신한다. */
    void refreshGauges() {
        try {
            Long len = ops().size(STREAM_KEY);
            streamLength.set(len == null ? 0 : len);
            var summary = ops().pending(STREAM_KEY, GROUP);
            pendingCount.set(summary == null ? 0 : summary.getTotalPendingMessages());
            lagMillis.set(readLagMillis());   // -1 = 알 수 없음
        } catch (Exception e) {
            log.debug("스트림 길이/적체를 읽지 못했다: {}", e.toString());
        }
    }

    /**
     * 소비가 얼마나 뒤처져 있는가 (ms). 스트림의 마지막 항목 시각 − 그룹이 마지막으로 배달한 시각.
     *
     * <p><b>왜 개수가 아니라 시간인가.</b> 항목 수(lag)를 쓰고 싶지만 spring-data-redis 3.2.5 의
     * {@code XInfoGroup} 에는 {@code lag}/{@code entries-read} 가 없다(그 필드는 Redis 7 에서 생겼고
     * 라이브러리가 아직 안 싣는다). 대신 항목 id 가 발행 시각(ms)을 담고 있어서
     * <b>얼마나 밀렸는지</b>는 같은 비용으로 나온다.
     *
     * <p><b>왜 pending 으로는 부족한가.</b> pending 은 "배달됐지만 ACK 전" 이다. 소비가 저장을
     * 못 따라가면 그 전에 <b>읽지도 못한 것</b>이 쌓이는데 pending 은 0 이다 -
     * B9r 사다리에서 1,600건/초일 때 pending 0 · 이 값이 수십 초였다. 그 상황에서 pending 경보는 조용했다.
     *
     * <p>정상 상태에서는 소비 주기(1초) 안에 비므로 1초 아래다.
     *
     * @return 밀린 시간(ms). 알 수 없으면 -1
     */
    private long readLagMillis() {
        try {
            var info = ops().info(STREAM_KEY);
            var groups = ops().groups(STREAM_KEY);
            if (info == null || groups == null || groups.isEmpty()) {
                return -1;
            }
            long newest = idMillis(info.lastGeneratedId());
            for (var group : groups) {
                if (GROUP.equals(group.groupName())) {
                    return Math.max(0, newest - idMillis(group.lastDeliveredId()));
                }
            }
            return -1;
        } catch (Exception e) {
            log.debug("스트림 밀린 시간을 읽지 못했다: {}", e.toString());
            return -1;
        }
    }

    /** 항목 id 는 {@code <ms>-<seq>} 다. 앞부분이 발행 시각이다. */
    private static long idMillis(String id) {
        if (id == null || id.isBlank()) {
            return 0;
        }
        int dash = id.indexOf('-');
        try {
            return Long.parseLong(dash < 0 ? id : id.substring(0, dash));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 시험에서 소비자 이름과 클레임 기준 시간을 확인한다. */
    String consumerName() {
        return consumerName;
    }

    private StreamOperations<String, Object, Object> ops() {
        return redis.opsForStream();
    }

    private static String value(MapRecord<String, Object, Object> record, String field) {
        Object v = record.getValue().get(field);
        return v == null ? null : String.valueOf(v);
    }

    private static Long longValue(MapRecord<String, Object, Object> record, String field) {
        String raw = value(record, field);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDateTime sentAt(MapRecord<String, Object, Object> record) {
        Long millis = longValue(record, F_SENT_AT);
        return millis == null ? null
                : LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "local";
        }
    }
}
