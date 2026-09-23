package com.edu.edumeet.chat.service;

import com.edu.edumeet.chat.domain.ChatMessage;
import com.edu.edumeet.chat.repository.ChatMessageRepository;
import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.domain.SessionType;
import com.edu.edumeet.member.domain.Member;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Redis Stream 저장 대기열. (#201)
 *
 * <p>B8 에서 메모리 큐는 {@code kill -9} 로 죽으면 <b>지표 없이</b> 사라졌다(20~30건).
 * 여기서 고정하는 것은 그 자리가 실제로 메워졌는가다.
 *
 * <p><b>소비 루프는 꺼 두고 손으로 돌린다.</b> 스케줄러가 1초마다 도는 것에 기대면
 * "읽고 ACK 전에 죽는" 순간을 만들 수 없다. {@code poll-ms} 를 크게 두고
 * {@code readNew/claimStale/persistAndAck} 를 직접 부른다 - 시험하려는 것이 그 셋이다.
 *
 * <p>클레임 기준 시간은 0 으로 둔다. 기다리지 않고 죽은 소비자의 항목을 가져오는지 본다.
 */
@SpringBootTest(properties = {
        "edumeet.chat.archive.mode=stream",
        "edumeet.chat.archive.stream.poll-ms=3600000",
        "edumeet.chat.archive.claim-min-idle-ms=0",
        "edumeet.chat.archive.stream.max-deliveries=2",
        // 비상 상한을 작게 잡아 그 경로를 시험한다 (기본 1,000,000 은 밀어 넣을 수 없다)
        "edumeet.chat.archive.stream.emergency-max-len=3",
})
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Redis Stream 저장 대기열")
class ChatArchiveStreamTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired ChatService chatService;
    @Autowired ChatArchiveStream archiveStream;
    @Autowired ChatArchiveQueue archiveQueue;
    @Autowired ChatMessageRepository chatMessageRepository;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired MeterRegistry registry;
    @PersistenceContext EntityManager em;

    @BeforeEach
    void clearStream() {
        // 컨테이너를 클래스가 공유한다. 앞 시험이 남긴 항목이 오면 개수 단언이 흔들린다.
        redisTemplate.delete(ChatArchiveStream.STREAM_KEY);
        archiveStream.ensureGroup();
    }

    @AfterEach
    void drainMemoryQueue() {
        // 폴백 경로를 쓰는 시험이 큐에 남긴 것을 치운다(공유 싱글턴이다).
        for (int i = 0; i < 50 && archiveQueue.queuedCount() > 0; i++) {
            archiveQueue.flush();
        }
    }

    @Test
    @DisplayName("★ 발행이 스트림으로 가고, 저장한 뒤에 ACK 한다")
    void publish_goes_to_stream_then_db_and_is_acked() {
        Long meetingId = givenMeeting();

        chatService.handle(meetingId, "viewer@test", "다시보기 메시지");

        assertThat(archiveQueue.queuedCount())
                .as("mode=stream 이면 메모리 큐로 가면 안 된다 - 그러면 죽을 때 사라진다")
                .isZero();
        assertThat(ops().size(ChatArchiveStream.STREAM_KEY)).isEqualTo(1);

        int saved = archiveStream.persistAndAck(archiveStream.readNew());

        assertThat(saved).isEqualTo(1);
        List<ChatMessage> rows = rowsOf(meetingId);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getContent()).isEqualTo("다시보기 메시지");
        assertThat(rows.get(0).getMessageUid())
                .as("uid 가 없으면 중복을 DB 가 못 막는다")
                .isNotBlank();
        assertThat(pending())
                .as("저장이 끝났으면 ACK 되어 pending 이 비어야 한다")
                .isZero();
    }

    @Test
    @DisplayName("★ 소비자가 ACK 전에 사라져도 다른 소비자가 가져가 저장한다 (kill -9 의 모양)")
    void unacked_entry_is_claimed_and_saved() {
        Long meetingId = givenMeeting();
        chatService.handle(meetingId, "viewer@test", "죽은 소비자가 읽은 메시지");

        // 소비자 A 가 읽는다. 그리고 저장 전에 죽는다 - ACK 를 못 한다.
        List<MapRecord<String, Object, Object>> read = archiveStream.readNew();
        assertThat(read).hasSize(1);
        assertThat(rowsOf(meetingId)).as("아직 저장되지 않았다").isEmpty();
        assertThat(pending()).as("항목은 pending 으로 남아 있어야 한다").isEqualTo(1);

        // 소비자 B 가 가져간다. 메모리 큐였다면 여기서 이미 사라졌다.
        int saved = archiveStream.persistAndAck(archiveStream.claimStale());

        assertThat(saved).isEqualTo(1);
        assertThat(rowsOf(meetingId))
                .as("읽은 소비자가 죽어도 항목은 살아남아 저장돼야 한다")
                .hasSize(1);
        assertThat(pending()).isZero();
        assertThat(counter("chat.archive.stream.claimed")).isPositive();
    }

    @Test
    @DisplayName("★ 같은 항목을 두 번 처리해도 DB 에는 한 행이다")
    void duplicate_delivery_saves_one_row() {
        Long meetingId = givenMeeting();
        chatService.handle(meetingId, "viewer@test", "중복 배달");

        List<MapRecord<String, Object, Object>> records = archiveStream.readNew();
        assertThat(archiveStream.persistAndAck(records)).isEqualTo(1);
        double duplicatesBefore = counter("chat.archive.stream.duplicates");

        // 같은 항목이 다시 온다 - 최소 한 번 전달이라 실제로 일어난다.
        assertThat(archiveStream.persistAndAck(records)).isZero();

        assertThat(rowsOf(meetingId)).hasSize(1);
        assertThat(counter("chat.archive.stream.duplicates")).isEqualTo(duplicatesBefore + 1);
    }

    @Test
    @DisplayName("★ 조회를 우회해 같은 uid 를 넣어도 DB 유니크가 막는다")
    void db_unique_index_blocks_same_uid() {
        Long meetingId = givenMeeting();
        String uid = "same-uid-" + SEQ.incrementAndGet();

        transactionTemplate.executeWithoutResult(status -> chatMessageRepository.saveAndFlush(
                ChatMessage.of(em.find(Meeting.class, meetingId), "a@test", "하나", 1L,
                        uid, LocalDateTime.now())));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                chatMessageRepository.saveAndFlush(
                        ChatMessage.of(em.find(Meeting.class, meetingId), "b@test", "둘", 2L,
                                uid, LocalDateTime.now()))))
                .as("조회와 삽입 사이의 창은 DB 제약만 막는다")
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(rowsOf(meetingId)).hasSize(1);
    }

    @Test
    @DisplayName("★ 안 읽은 항목도, ACK 전 항목도 트리밍이 지우지 않는다 (검토 6)")
    void trim_never_deletes_unread_or_pending_entries() {
        Long meetingId = givenMeeting();
        for (int i = 0; i < 3; i++) {
            chatService.handle(meetingId, "viewer@test", "안 읽은 메시지 " + i);
        }
        long unread = ops().size(ChatArchiveStream.STREAM_KEY);
        assertThat(unread).isEqualTo(3);

        // 1) 아무도 안 읽은 상태 - 지우면 그게 유실이다
        archiveStream.trimStream();
        assertThat(ops().size(ChatArchiveStream.STREAM_KEY))
                .as("읽지도 않은 항목을 지우면 유실이다 - 메모리 보호는 삭제가 아니라 경보가 한다")
                .isEqualTo(unread);

        // 2) 읽었지만 ACK 전 - 처리 중인 것을 지우면 복구할 수 없다
        List<MapRecord<String, Object, Object>> records = archiveStream.readNew();
        assertThat(records).hasSize(3);
        archiveStream.trimStream();
        assertThat(ops().size(ChatArchiveStream.STREAM_KEY))
                .as("ACK 전 항목을 지우면 클레임으로도 못 되살린다")
                .isEqualTo(unread);

        // 3) ACK 뒤에는 지워진다 - 안 지우면 Redis 가 무한히 큰다
        archiveStream.persistAndAck(records);
        archiveStream.trimStream();
        assertThat(ops().size(ChatArchiveStream.STREAM_KEY)).isLessThan(unread);
    }

    @Test
    @DisplayName("★ 이미 저장된 uid 가 배치에 섞여도 나머지는 저장되고 ACK 된다 (검토 4)")
    void batch_with_already_saved_uid_still_saves_the_rest() {
        Long meetingId = givenMeeting();
        String taken = "taken-" + SEQ.incrementAndGet();
        String fresh = "fresh-" + SEQ.incrementAndGet();

        // 다른 소비자가 먼저 저장해 둔 행
        transactionTemplate.executeWithoutResult(status -> chatMessageRepository.saveAndFlush(
                ChatMessage.of(em.find(Meeting.class, meetingId), "other@test", "먼저 저장된 것",
                        1L, taken, LocalDateTime.now())));

        addToStream(taken, meetingId, "같은 uid 로 다시 온 것");
        addToStream(fresh, meetingId, "정상 항목");

        int saved = archiveStream.persistAndAck(archiveStream.readNew());

        assertThat(saved).as("정상 항목은 저장돼야 한다").isEqualTo(1);
        assertThat(rowsOf(meetingId)).hasSize(2);
        assertThat(pending()).as("배치가 ACK 됐다 - 중복 하나가 배치를 막으면 안 된다").isZero();
    }

    @Test
    @DisplayName("★ 행 단위 재시도는 중복만 건너뛰고 나머지를 살린다 (검토 4)")
    void row_by_row_retry_only_skips_the_duplicate() {
        Long meetingId = givenMeeting();
        String uid = "race-" + SEQ.incrementAndGet();

        List<ChatMessage> rows = List.of(
                ChatMessage.of(em.find(Meeting.class, meetingId), "a@test", "하나", 1L, uid, LocalDateTime.now()),
                ChatMessage.of(em.find(Meeting.class, meetingId), "b@test", "둘", 2L, uid, LocalDateTime.now()));

        ChatArchiveStream.SaveOutcome outcome = archiveStream.saveRowByRow(rows);

        assertThat(outcome.saved()).as("한 행은 들어가야 한다").isEqualTo(1);
        assertThat(outcome.duplicates()).as("유니크 위반은 중복으로 세야 한다").isEqualTo(1);
        assertThat(rowsOf(meetingId)).hasSize(1);
    }

    @Test
    @DisplayName("★ 저장할 수 없는 행이 배치를 막지 않는다 (검토 2)")
    void unusable_row_does_not_block_the_batch() {
        Long meetingId = givenMeeting();
        // uid 컬럼은 VARCHAR(36) 이다. 넘기면 영원히 실패한다.
        addToStream("x".repeat(60), meetingId, "저장할 수 없는 행");
        addToStream("ok-" + SEQ.incrementAndGet(), meetingId, "정상 항목");
        double discardedBefore = counter("chat.archive.stream.discarded");

        int saved = archiveStream.persistAndAck(archiveStream.readNew());

        assertThat(saved).as("정상 항목은 저장돼야 한다").isEqualTo(1);
        assertThat(counter("chat.archive.stream.discarded")).isGreaterThan(discardedBefore);
        assertThat(pending()).as("버릴 행 하나가 배치를 영원히 막으면 뒤가 전부 못 나간다").isZero();
    }

    @Test
    @DisplayName("★ 여러 번 배달된 항목은 죽은 편지함으로 격리된다 (검토 2)")
    void repeatedly_failed_entry_is_dead_lettered() {
        Long meetingId = givenMeeting();
        chatService.handle(meetingId, "viewer@test", "계속 실패하는 항목");

        List<MapRecord<String, Object, Object>> read = archiveStream.readNew();
        assertThat(read).hasSize(1);
        // 다른 소비자가 한 번 더 가져간다 → 배달 횟수 2 (시험 설정의 한계값)
        ops().claim(ChatArchiveStream.STREAM_KEY, ChatArchiveStream.GROUP, "other-consumer",
                java.time.Duration.ZERO, read.get(0).getId());
        double deadBefore = counter("chat.archive.stream.dead");

        archiveStream.claimStale();

        assertThat(counter("chat.archive.stream.dead")).isEqualTo(deadBefore + 1);
        assertThat(pending()).as("격리한 항목은 ACK 되어 pending 에 남지 않는다").isZero();
        assertThat(ops().size(ChatArchiveStream.DEAD_KEY))
                .as("본문을 그대로 복사해 둬야 나중에 무엇이었는지 볼 수 있다")
                .isEqualTo(1);
        assertThat(rowsOf(meetingId)).as("격리한 것은 저장되지 않는다").isEmpty();
        redisTemplate.delete(ChatArchiveStream.DEAD_KEY);
    }

    @Test
    @DisplayName("★ 그룹이 없으면 트리밍 경계를 만들지 않고, 비상 트리밍은 계속 돈다 (검토 1)")
    void trim_without_group_still_runs_the_emergency_valve() {
        // 그룹을 지운다. XPENDING 을 부르면 NOGROUP 이 난다.
        redisTemplate.delete(ChatArchiveStream.STREAM_KEY);
        Long meetingId = givenMeeting();
        for (int i = 0; i < 5; i++) {
            addToStream("nogroup-" + i + "-" + SEQ.incrementAndGet(), meetingId, "그룹 없는 항목 " + i);
        }
        assertThat(ops().size(ChatArchiveStream.STREAM_KEY)).isEqualTo(5);
        double trimmedBefore = counter("chat.archive.stream.trimmed");

        assertThat(archiveStream.trimFloor())
                .as("그룹이 없는데 XPENDING 을 부르면 NOGROUP 이 난다 - 부르지 않고 null 이어야 한다")
                .isNull();
        archiveStream.trimStream();

        assertThat(ops().size(ChatArchiveStream.STREAM_KEY))
                .as("경계를 못 만들어도 비상 상한(시험 설정 3)은 지켜져야 한다")
                .isLessThanOrEqualTo(3);
        assertThat(counter("chat.archive.stream.trimmed")).isGreaterThan(trimmedBefore);
    }

    @Test
    @DisplayName("★ 복사 후 ACK 전에 멈췄다가 다시 돌려도 죽은 편지함에 한 건이다 (검토 3)")
    void quarantine_is_idempotent() {
        Long meetingId = givenMeeting();
        chatService.handle(meetingId, "viewer@test", "계속 실패하는 항목");
        List<MapRecord<String, Object, Object>> read = archiveStream.readNew();
        RecordId id = read.get(0).getId();

        // 1) 복사만 하고 ACK 전에 죽은 상태를 만든다 - 원본과 **같은 id** 로 죽은 편지함에 넣는다
        Map<String, String> body = new HashMap<>();
        read.get(0).getValue().forEach((k, v) -> body.put(String.valueOf(k), String.valueOf(v)));
        ops().add(org.springframework.data.redis.connection.stream.StreamRecords
                .mapBacked(body).withStreamKey(ChatArchiveStream.DEAD_KEY).withId(id));
        assertThat(pending()).as("복사만 했으니 원본은 아직 pending 이다").isEqualTo(1);

        // 2) 다시 돈다 - 같은 id 라 Redis 가 두 번째 XADD 를 거부한다. 그걸 '이미 격리됨' 으로 봐야 한다.
        PendingMessages pendingMessages = ops().pending(ChatArchiveStream.STREAM_KEY,
                ChatArchiveStream.GROUP, org.springframework.data.domain.Range.unbounded(), 10);
        int moved = archiveStream.quarantine(java.util.List.of(pendingMessages.get(0)));

        assertThat(moved).isEqualTo(1);
        assertThat(ops().size(ChatArchiveStream.DEAD_KEY))
                .as("재시도가 격리본을 늘리면 안 된다")
                .isEqualTo(1);
        assertThat(pending()).as("이미 옮겨진 것은 ACK 되어 pending 에서 빠진다").isZero();
        redisTemplate.delete(ChatArchiveStream.DEAD_KEY);
    }

    private void addToStream(String uid, Long meetingId, String content) {
        Map<String, String> body = new HashMap<>();
        body.put("uid", uid);
        body.put("meetingId", String.valueOf(meetingId));
        body.put("sender", "raw@test");
        body.put("content", content);
        body.put("offsetMillis", "1000");
        body.put("sentAt", String.valueOf(System.currentTimeMillis()));
        ops().add(ChatArchiveStream.STREAM_KEY, body);
    }

    private Long givenMeeting() {
        int n = SEQ.incrementAndGet();
        Long[] id = new Long[1];
        transactionTemplate.executeWithoutResult(status -> {
            Member owner = Member.builder()
                    .email("stream-" + n + "@test").nickname("호스트").password("x").build();
            em.persist(owner);
            ClassRoom c = ClassRoom.builder().member(owner)
                    .title("클래스").description("-").participantLimit(30).isDeleted(false).build();
            em.persist(c);
            Meeting m = Meeting.builder().classRoom(c)
                    .title("세션").description("-").sessionType(SessionType.BROADCAST)
                    .startTime(LocalDateTime.now().minusMinutes(1)).build();
            em.persist(m);
            em.flush();
            id[0] = m.getId();
        });
        return id[0];
    }

    private List<ChatMessage> rowsOf(Long meetingId) {
        return chatMessageRepository.findAll().stream()
                .filter(m -> m.getMeeting().getId().equals(meetingId))
                .toList();
    }

    private long pending() {
        var summary = ops().pending(ChatArchiveStream.STREAM_KEY, ChatArchiveStream.GROUP);
        return summary == null ? 0 : summary.getTotalPendingMessages();
    }

    private org.springframework.data.redis.core.StreamOperations<String, Object, Object> ops() {
        return redisTemplate.opsForStream();
    }

    private double counter(String name) {
        var c = registry.find(name).counter();
        return c == null ? 0 : c.count();
    }
}
