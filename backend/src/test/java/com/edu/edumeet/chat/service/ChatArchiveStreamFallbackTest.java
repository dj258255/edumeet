package com.edu.edumeet.chat.service;

import com.edu.edumeet.chat.repository.ChatMessageRepository;
import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.domain.SessionType;
import com.edu.edumeet.member.domain.Member;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Redis 가 죽었을 때 발행이 멈추지 않는지. (#201)
 *
 * <p>설계에서 정한 것: <b>XADD 실패는 발행을 막지 않는다.</b> 기존 메모리 큐로 떨어진다 -
 * 방송이 멈추는 것보다 다시보기가 조금 비는 게 낫다는 기존 원칙(#61) 그대로다.
 * 떨어진 수는 {@code chat.archive.stream.fallback} 으로 센다.
 *
 * <p>Redis 를 진짜로 죽이지 않고 <b>닫힌 포트</b>를 가리킨다(127.0.0.1:1).
 * 연결은 처음 쓸 때 만들어지므로 컨텍스트 기동에는 지장이 없고, 실패 경로는 같은 코드를 탄다.
 */
@SpringBootTest(properties = {
        "edumeet.chat.archive.mode=stream",
        "edumeet.chat.archive.stream.poll-ms=3600000",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=1",
})
@ActiveProfiles("test")
@DisplayName("Redis 가 죽었을 때의 발행")
class ChatArchiveStreamFallbackTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired ChatService chatService;
    @Autowired ChatArchiveQueue archiveQueue;
    @Autowired ChatMessageRepository chatMessageRepository;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired MeterRegistry registry;
    @PersistenceContext EntityManager em;

    @Test
    @DisplayName("★ Redis 가 죽어도 발행은 성공하고, 메모리 큐로 떨어지며, 지표가 오른다")
    void redis_down_falls_back_to_memory_queue() {
        Long meetingId = givenMeeting();
        double fallbackBefore = counter("chat.archive.stream.fallback");

        assertThatCode(() -> chatService.handle(meetingId, "viewer@test", "Redis 없는 메시지"))
                .as("Redis 가 죽었다고 방송이 멈추면 안 된다")
                .doesNotThrowAnyException();

        assertThat(counter("chat.archive.stream.fallback"))
                .as("떨어진 수를 세지 않으면 Redis 문제를 아무도 모른다")
                .isEqualTo(fallbackBefore + 1);
        assertThat(archiveQueue.queuedCount())
                .as("메모리 큐로 떨어져야 한다")
                .isEqualTo(1);

        // 떨어진 것도 결국 저장된다 - 폴백이 '버리기' 가 아니라는 것.
        archiveQueue.flush();
        assertThat(chatMessageRepository.findAll().stream()
                .filter(m -> m.getMeeting().getId().equals(meetingId)).count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("★ 연속 실패가 기준을 넘어야 차단 창이 열린다 (검토 2)")
    void breaker_opens_only_after_consecutive_failures() {
        Long meetingId = givenMeeting();
        assertThat(breakerOpen())
                .as("아직 실패가 없다 - 창이 열려 있으면 안 된다")
                .isZero();

        // Redis 는 이 시험 클래스에서 닫힌 포트다. 발행은 매번 실패하고 폴백한다.
        for (int i = 0; i < 3; i++) {
            chatService.handle(meetingId, "viewer@test", "실패 " + i);
        }

        assertThat(breakerOpen())
                .as("연속 3회 실패하면 창이 열려 발행 경로가 Redis 를 안 부른다")
                .isEqualTo(1.0);
    }

    private double breakerOpen() {
        var gauge = registry.find("chat.archive.stream.breaker.open").gauge();
        return gauge == null ? 0 : gauge.value();
    }

    private Long givenMeeting() {
        int n = SEQ.incrementAndGet();
        Long[] id = new Long[1];
        transactionTemplate.executeWithoutResult(status -> {
            Member owner = Member.builder()
                    .email("fallback-" + n + "@test").nickname("호스트").password("x").build();
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

    private double counter(String name) {
        var c = registry.find(name).counter();
        return c == null ? 0 : c.count();
    }
}
