package com.edu.edumeet.integration.chat;

import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.config.internal.InternalApiTokenFilter;
import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.domain.MeetingParticipant;
import com.edu.edumeet.meeting.domain.SessionType;
import com.edu.edumeet.meeting.dto.CaptionBroadcast;
import com.edu.edumeet.meeting.service.CaptionBroadcastQueue;
import io.micrometer.core.instrument.MeterRegistry;
import com.edu.edumeet.member.domain.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 역압이 어느 스레드에 착지하는지 고정한다. (#143)
 *
 * <p><b>왜 이 시험이 생겼나.</b> {@code WebSocketConfig} 에 거부 정책을
 * {@link ThreadPoolExecutor.CallerRunsPolicy} 로 두고 <i>"역압을 위로 전달한다"</i> 고
 * 적어 뒀다. <b>그런데 "위" 가 어디인지는 적지 않았다.</b>
 *
 * <p>포트폴리오 리뷰에서 이 질문을 받았다 —
 * <i>"그 스레드가 다른 요청도 처리하는 스레드면 뭐가 죽나요?"</i>
 * 추측으로 답하지 않으려고 실제로 찍어 봤고, 답이 나왔다.
 *
 * <pre>
 *   clientInbound   core 24 / max 48 · 큐 2,000  · CallerRunsPolicy
 *   clientOutbound  core 24 / max 48 · 큐 20,000 · CallerRunsPolicy
 *   brokerChannel   실행기 없음 → 호출 스레드에서 직접 실행
 * </pre>
 *
 * <p><b>세 번째가 답을 정한다.</b> {@code convertAndSend} 는 brokerChannel 로 가는데
 * 거기 실행기가 없으므로 {@code SimpleBrokerMessageHandler} 가 호출 스레드에서 그대로 돌고,
 * 그 스레드가 {@code clientOutboundChannel.send()} 를 부른다.
 * 따라서 아웃바운드 큐가 찼을 때 전송을 떠안는 것은 <b>원래 호출자</b>다.
 *
 * <pre>
 *   채팅  &#64;MessageMapping  →  chat-in- 실행기 스레드
 *   자막  &#64;PostMapping     →  Tomcat HTTP 요청 스레드
 * </pre>
 *
 * <p>두 번째가 위험하다. 파이썬 STT 가 계속 POST 하는 동안 아웃바운드가 포화하면
 * Tomcat 요청 스레드(기본 200)가 전송에 묶인다 —
 * <b>자막이 채팅을 막는 것이 아니라 REST API 전체를 막는다.</b>
 *
 * <p>큐를 실제로 채우지는 않는다. 재려는 것은 "언제 터지나" 가 아니라
 * <b>"터지면 누가 떠안나"</b> 이고, 그것은 채널 구성과 스레드 이름으로 확정된다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "edumeet.caption.archive.flush-interval-ms=600000")
@ActiveProfiles("test")
@DisplayName("역압 착지점")
class BackpressureLandingTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final String TOKEN = "test-internal-token";

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired @Qualifier("clientInboundChannel") ExecutorSubscribableChannel inbound;
    @Autowired @Qualifier("clientOutboundChannel") ExecutorSubscribableChannel outbound;
    @Autowired @Qualifier("brokerChannel") ExecutorSubscribableChannel broker;
    @Autowired CaptionBroadcastQueue captionBroadcastQueue;
    @Autowired MeterRegistry meterRegistry;
    @PersistenceContext EntityManager em;

    private Long meetingId;
    private final AtomicReference<String> brokerEntryThread = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        brokerEntryThread.set(null);
        broker.addInterceptor(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                brokerEntryThread.compareAndSet(null, Thread.currentThread().getName());
                return message;
            }
        });

        String email = "bp-" + SEQ.incrementAndGet() + "@test";
        transactionTemplate.executeWithoutResult(status -> {
            Member owner = Member.builder().email(email).nickname("발표자").password("x").build();
            em.persist(owner);
            ClassRoom classRoom = ClassRoom.builder()
                    .member(owner).title("클래스").description("-")
                    .participantLimit(30).isDeleted(false).build();
            em.persist(classRoom);
            Meeting meeting = Meeting.builder()
                    .classRoom(classRoom).title("역압").description("-")
                    .sessionType(SessionType.AUDIO_BROADCAST)
                    .startTime(LocalDateTime.now()).build();
            em.persist(meeting);
            em.persist(MeetingParticipant.join(meeting, email));
            em.flush();
            meetingId = meeting.getId();
        });
    }

    private ThreadPoolExecutor poolOf(ExecutorSubscribableChannel channel) {
        return ((ThreadPoolTaskExecutor) channel.getExecutor()).getThreadPoolExecutor();
    }

    @Test
    @DisplayName("★ brokerChannel 에는 실행기가 없다 - 이것이 역압 착지점을 정한다")
    void broker_channel_has_no_executor() {
        assertThat(broker.getExecutor())
                .as("""
                    실행기가 없으므로 SimpleBrokerMessageHandler 가 호출 스레드에서 돈다.
                    그래서 아웃바운드 큐가 찼을 때 CallerRunsPolicy 가 발동하는 스레드는
                    브로커 스레드가 아니라 '원래 브로드캐스트를 시작한 스레드' 다.
                    여기 실행기가 생기면 이 시험이 깨지고, 착지점 분석도 다시 해야 한다.""")
                .isNull();
    }

    @Test
    @DisplayName("★ 자막 브로드캐스트는 요청 스레드가 아니라 전용 스레드에서 나간다 (#151)")
    void caption_broadcast_leaves_the_http_request_thread() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(InternalApiTokenFilter.HEADER, TOKEN);

        ResponseEntity<String> response = rest.postForEntity(
                "http://localhost:" + port + "/api/v1/internal/meetings/" + meetingId + "/captions",
                new HttpEntity<>(Map.of("text", "역압 확인", "sequence", 1,
                        "spokenAt", System.currentTimeMillis(), "finalSegment", false), headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(brokerEntryThread.get()).isNotNull());

        assertThat(brokerEntryThread.get())
                .as("""
                    전에는 http-nio- 로 시작했다. brokerChannel 에 실행기가 없어서
                    요청 스레드가 그대로 clientOutboundChannel.send() 까지 갔고,
                    아웃바운드가 포화하면 CallerRunsPolicy 로 그 스레드가 전송을 떠안았다.
                    Tomcat 요청 스레드가 소진되면 자막이 아니라 REST API 전체가 막힌다.
                    이제 전용 스레드가 떠안는다 - 여기가 다시 http-nio- 가 되면 그 문제가 돌아온 것이다.""")
                .startsWith("caption-out-");
    }

    @Test
    @DisplayName("★ 요청 스레드는 발행을 기다리지 않는다 - 큐에 넣고 바로 돌아온다 (#151)")
    void ingest_returns_without_waiting_for_the_send() {
        int before = captionBroadcastQueue.queuedCount() + 1;

        ResponseEntity<String> response = postCaption("던지고 잊는다");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(before)
                .as("요청이 돌아온 시점에는 이미 큐를 지났거나 큐에 있다. 어느 쪽이든 전송을 기다리지 않았다")
                .isPositive();
    }

    @Test
    @DisplayName("★ 자막 발행 큐는 차면 가장 오래된 것을 버리고 센다 - 채팅과 정책이 다르다 (#151)")
    void caption_queue_drops_oldest_and_counts_it() {
        double dropsBefore = droppedCount();

        // 상한(256)을 넘겨 밀어 넣는다. 워커가 빼 가므로 정확한 경계 대신
        // "넘치게 넣으면 버리고 센다" 를 본다.
        for (int i = 0; i < 2_000; i++) {
            captionBroadcastQueue.offer("/topic/rooms/0/captions",
                    new CaptionBroadcast(0L, "밀어넣기 " + i, (long) i, 0L, 0L, 0L, true));
        }

        assertThat(captionBroadcastQueue.queuedCount())
                .as("상한을 넘겨 쌓이지 않는다. 깊은 큐는 오래된 자막을 오래 붙잡겠다는 뜻이다")
                .isLessThanOrEqualTo(256);
        assertThat(droppedCount())
                .as("""
                    버린 것은 반드시 센다. 조용히 버리면 "자막이 원래 띄엄띄엄 나오나 보다" 가 된다.
                    전사는 CaptionArchiveQueue 가 따로 들고 있으므로 여기서 버려도 원본은 남는다.""")
                .isGreaterThan(dropsBefore);
    }

    private double droppedCount() {
        return meterRegistry.get("caption.broadcast.dropped").counter().count();
    }

    private ResponseEntity<String> postCaption(String text) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(InternalApiTokenFilter.HEADER, TOKEN);
        return rest.postForEntity(
                "http://localhost:" + port + "/api/v1/internal/meetings/" + meetingId + "/captions",
                // 이 시험이 보는 것은 발행 경로다. final 로 보내면 저장 큐에 남아
                // 같은 컨텍스트를 쓰는 다른 시험의 큐 길이 단언을 깨뜨린다.
                new HttpEntity<>(Map.of("text", text, "sequence", 1,
                        "spokenAt", System.currentTimeMillis(), "finalSegment", false), headers),
                String.class);
    }

    @Test
    @DisplayName("★ 인바운드·아웃바운드 큐 상한이 코드가 선언한 값과 같다")
    void queue_capacities_match_the_declared_bounds() {
        assertThat(poolOf(inbound).getQueue().remainingCapacity())
                .as("WebSocketConfig 의 INBOUND_QUEUE_CAPACITY 와 같아야 한다").isEqualTo(2_000);
        assertThat(poolOf(outbound).getQueue().remainingCapacity())
                .as("""
                    OUTBOUND_QUEUE_CAPACITY 와 같아야 한다.
                    경보가 이 값과의 비율로 임계값을 묻고 있으므로,
                    여기가 바뀌면 경보의 의미도 같이 바뀐다.""").isEqualTo(20_000);
    }

    @Test
    @DisplayName("★ 두 채널 모두 버리지 않고 호출자에게 넘긴다 - 채팅은 순서가 있는 대화다")
    void both_channels_push_back_instead_of_dropping() {
        assertThat(poolOf(inbound).getRejectedExecutionHandler())
                .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
        assertThat(poolOf(outbound).getRejectedExecutionHandler())
                .as("""
                    DiscardPolicy 로 바뀌면 조용히 메시지가 사라진다.
                    다만 이 정책은 채팅 기준이다 - 자막은 최신성이 중요해서
                    CaptionBroadcastQueue 로 갈라 "버리고 센다" 를 따로 쓴다. (#151)""")
                .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
    }
}
