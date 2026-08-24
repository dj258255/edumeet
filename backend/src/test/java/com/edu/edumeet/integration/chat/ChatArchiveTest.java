package com.edu.edumeet.integration.chat;

import com.edu.edumeet.chat.domain.ChatMessage;
import com.edu.edumeet.chat.repository.ChatMessageRepository;
import com.edu.edumeet.chat.service.ChatArchiveQueue;
import com.edu.edumeet.chat.service.ChatService;
import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.domain.SessionType;
import com.edu.edumeet.member.domain.Member;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.time.Duration.ofSeconds;

/**
 * 다시보기 채팅 - 발행 경로 밖에서 저장한다. (#61)
 *
 * <pre>
 *   전    발행 -> DB write(동기) -> 브로드캐스트
 *   후    발행 -> 브로드캐스트
 *              ↘ 큐 -> 배치 flush -> DB
 * </pre>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("다시보기 채팅")
class ChatArchiveTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired ChatService chatService;
    @Autowired ChatArchiveQueue archiveQueue;
    @Autowired ChatMessageRepository chatMessageRepository;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired MeterRegistry registry;
    @PersistenceContext EntityManager em;

    /**
     * 큐는 애플리케이션 컨텍스트를 공유하는 싱글턴이다.
     *
     * <p>홍수 시험이 6,000건을 밀어 넣고 나면 큐가 가득 찬 채로 남아,
     * <b>다음 시험의 메시지가 그 뒤에 밀리거나 버려진다.</b>
     * JUnit 은 메서드 순서를 보장하지 않으므로 <b>실행마다 다른 시험이 실패한다</b> -
     * 실제로 BROADCAST 만 실패하고 AUDIO_BROADCAST 는 통과하는 모양으로 나왔다.
     * 같은 코드 경로인데 결과가 갈리면 그건 제품이 아니라 시험 탓이다.
     *
     * <p>공유 상태를 쓰면 명시적으로 치운다.
     */
    @AfterEach
    void drainQueue() {
        for (int i = 0; i < 100 && archiveQueue.queuedCount() > 0; i++) {
            archiveQueue.flush();
        }
    }

    private Long givenMeeting(SessionType type, LocalDateTime startedAt) {
        int n = SEQ.incrementAndGet();
        Long[] id = new Long[1];
        transactionTemplate.executeWithoutResult(s -> {
            Member owner = Member.builder()
                    .email("archive-" + n + "@test").nickname("호스트").password("x").build();
            em.persist(owner);
            ClassRoom c = ClassRoom.builder().member(owner)
                    .title("클래스").description("-").participantLimit(30).isDeleted(false).build();
            em.persist(c);
            Meeting m = Meeting.builder().classRoom(c)
                    .title("세션").description("-").sessionType(type)
                    .startTime(startedAt).build();
            em.persist(m);
            em.flush();
            id[0] = m.getId();
        });
        return id[0];
    }

    private double counter(String name) {
        var c = registry.find(name).counter();
        return c == null ? 0 : c.count();
    }

    @ParameterizedTest
    @EnumSource(value = SessionType.class, names = {"BROADCAST", "AUDIO_BROADCAST"})
    @DisplayName("★ 방송 채팅도 저장된다 - 다시보기가 반쪽이 되지 않게")
    void broadcast_chat_is_archived(SessionType type) {
        Long meetingId = givenMeeting(type, LocalDateTime.now().minusMinutes(3));

        chatService.handle(meetingId, "viewer@test", "다시보기에서 보일 메시지");

        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            List<ChatMessage> saved = chatMessageRepository.findAll().stream()
                    .filter(m -> m.getMeeting().getId().equals(meetingId)).toList();
            assertThat(saved)
                    .as("%s 채팅이 저장되지 않았다. 다시보기에서 채팅이 사라진다", type)
                    .hasSize(1);
            assertThat(saved.get(0).getContent()).isEqualTo("다시보기에서 보일 메시지");
        });
    }

    @Test
    @DisplayName("★ 발행이 저장을 기다리지 않는다 - 반환 시점에는 아직 DB 에 없다")
    void publish_does_not_wait_for_the_write() {
        Long meetingId = givenMeeting(SessionType.BROADCAST, LocalDateTime.now().minusMinutes(1));

        chatService.handle(meetingId, "viewer@test", "즉시 반환되어야 한다");

        // 반환 직후에는 큐에만 있고 DB 에는 없다.
        // 이게 이 작업의 요점이다 - 발행 경로에서 DB 쓰기를 뺐다.
        long persistedNow = chatMessageRepository.findAll().stream()
                .filter(m -> m.getMeeting().getId().equals(meetingId)).count();
        assertThat(persistedNow)
                .as("반환 시점에 이미 저장돼 있다면 동기 쓰기다. 발행 경로에서 뺀 의미가 없다")
                .isZero();

        // 그리고 잠시 뒤에는 들어와 있다.
        await().atMost(ofSeconds(10)).untilAsserted(() ->
                assertThat(chatMessageRepository.findAll().stream()
                        .filter(m -> m.getMeeting().getId().equals(meetingId)).count())
                        .isEqualTo(1));
    }

    @Test
    @DisplayName("★ 재생 위치(offsetMillis)가 담긴다 - 없으면 다시보기에서 순서를 못 맞춘다")
    void offset_is_recorded() {
        LocalDateTime startedAt = LocalDateTime.now().minusMinutes(5);
        Long meetingId = givenMeeting(SessionType.BROADCAST, startedAt);

        chatService.handle(meetingId, "viewer@test", "5분쯤 지난 시점");

        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            var saved = chatMessageRepository.findAll().stream()
                    .filter(m -> m.getMeeting().getId().equals(meetingId)).findFirst();
            assertThat(saved).isPresent();
            Long offset = saved.get().getOffsetMillis();
            assertThat(offset)
                    .as("offsetMillis 가 없으면 재생 위치에 맞춰 보여줄 수 없다")
                    .isNotNull();
            // 5분 = 300,000ms. 테스트 실행 지연을 감안해 넉넉히 본다.
            assertThat(offset).isBetween(300_000L - 5_000L, 300_000L + 60_000L);
        });
    }

    @Test
    @DisplayName("★ 큐가 가득 차면 버린다 - 무한 큐를 다시 만들지 않는다")
    void full_queue_drops_instead_of_growing() {
        Long meetingId = givenMeeting(SessionType.BROADCAST, LocalDateTime.now());
        double droppedBefore = counter("chat.archive.dropped");

        // 상한(2,000)보다 훨씬 많이 밀어 넣는다. 배치가 동시에 빼가므로
        // "정확히 몇 개가 버려지는가" 는 재현되지 않는다. 재현되는 것은
        // (1) 큐가 상한을 넘지 않는다 (2) 넘친 만큼 dropped 가 오른다 이다.
        for (int i = 0; i < 6_000; i++) {
            archiveQueue.offer(meetingId, "flood@test", "m" + i, (long) i);
            assertThat(archiveQueue.queuedCount())
                    .as("큐가 상한을 넘었다. #43 의 OOM 을 다시 만드는 길이다")
                    .isLessThanOrEqualTo(2_000);
        }

        assertThat(counter("chat.archive.dropped"))
                .as("상한의 3배를 밀어 넣었는데 버린 게 없다면 어딘가 무한히 쌓이고 있다")
                .isGreaterThan(droppedBefore);
    }

    @Test
    @DisplayName("화상강의는 그대로 동기 저장한다 - 수업 기록은 유실되면 안 된다")
    void interactive_still_persists_inline() {
        Long meetingId = givenMeeting(SessionType.INTERACTIVE, LocalDateTime.now().minusMinutes(1));

        chatService.handle(meetingId, "student@test", "수업 중 질문");

        // 대기 없이 즉시 있어야 한다.
        assertThat(chatMessageRepository.findAll().stream()
                .filter(m -> m.getMeeting().getId().equals(meetingId)).count())
                .as("화상강의는 동기 저장이다. 비동기로 바꾸면 수업 기록이 유실될 수 있다")
                .isEqualTo(1);
    }
}
