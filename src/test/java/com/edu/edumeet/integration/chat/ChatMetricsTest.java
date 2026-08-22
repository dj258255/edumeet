package com.edu.edumeet.integration.chat;

import com.edu.edumeet.chat.dto.ChatMessageResponse;
import com.edu.edumeet.chat.dto.ChatSendRequest;
import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.config.jwt.JwtService;
import com.edu.edumeet.member.domain.Member;
import com.edu.edumeet.openvidu.domain.Meeting;
import com.edu.edumeet.openvidu.domain.MeetingParticipant;
import com.edu.edumeet.openvidu.domain.SessionType;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 채팅 지표가 실제로 /actuator/prometheus 에 나오는지 확인한다. (#39)
 *
 * <p>계측 코드를 쓰고 "지표를 붙였다" 고 하면 안 된다.
 * #28 에서 <b>설정은 있는데 엔드포인트가 404 였던</b> 일을 겪었다.
 * 여기서는 <b>연결·구독·발행을 실제로 하고 그 수가 지표에 반영되는지</b>까지 본다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
@ActiveProfiles("test")
@DisplayName("채팅 지표")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChatMetricsTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @LocalServerPort int port;
    @LocalManagementPort int managementPort;

    @Autowired TestRestTemplate rest;
    @Autowired JwtService jwtService;
    @Autowired TransactionTemplate transactionTemplate;
    @PersistenceContext EntityManager em;

    private WebSocketStompClient stompClient;
    private Long meetingId;
    private String token;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        String email = "metrics-" + SEQ.incrementAndGet() + "@test";
        transactionTemplate.executeWithoutResult(status -> {
            Member owner = Member.builder().email(email).nickname("참가자").password("x").build();
            em.persist(owner);
            ClassRoom classRoom = ClassRoom.builder()
                    .member(owner).title("클래스").description("-")
                    .participantLimit(30).isDeleted(false).build();
            em.persist(classRoom);
            Meeting meeting = Meeting.builder()
                    .classRoom(classRoom).title("회의").description("-")
                    .sessionType(SessionType.INTERACTIVE)
                    .startTime(LocalDateTime.now().minusMinutes(5)).build();
            em.persist(meeting);
            em.persist(MeetingParticipant.join(meeting, email));
            em.flush();
            meetingId = meeting.getId();
        });
        token = jwtService.generateAccessToken(1L, email);
    }

    @AfterEach
    void tearDown() {
        if (stompClient != null) stompClient.stop();
    }

    private String scrape() {
        return rest.getForObject(
                "http://localhost:" + managementPort + "/actuator/prometheus", String.class);
    }

    /** Prometheus 텍스트에서 값 하나를 읽는다. 이름이 없으면 null. */
    private Double metric(String body, String name) {
        Matcher m = Pattern.compile("^" + Pattern.quote(name) + "\\{[^}]*\\}\\s+([0-9.eE+-]+)$",
                Pattern.MULTILINE).matcher(body);
        return m.find() ? Double.parseDouble(m.group(1)) : null;
    }

    private StompSession connect() throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + token);
        return stompClient.connectAsync("ws://localhost:" + port + "/ws",
                        new WebSocketHttpHeaders(), headers, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
    }

    @Test
    @Order(1)
    @DisplayName("★ 큐 길이 지표가 존재한다 - 없으면 붕괴 2단계는 관측 자체가 불가능하다")
    void executor_queue_metrics_exist() {
        String body = scrape();

        assertThat(body)
                .as("기본 실행기는 큐가 무한이라 포화가 에러가 아니라 지연으로만 나타난다. "
                    + "이 지표가 없으면 그 상태를 볼 방법이 없다")
                .contains("executor_queued_tasks")
                .contains("clientInboundChannelExecutor")
                .contains("clientOutboundChannelExecutor");
    }

    @Test
    @Order(2)
    @DisplayName("연결하면 세션 수가 오르고 끊으면 내려간다")
    void session_gauge_tracks_connections() throws Exception {
        double before = orZero(metric(scrape(), "chat_sessions_active"));

        StompSession session = connect();
        Thread.sleep(400);
        assertThat(metric(scrape(), "chat_sessions_active"))
                .as("연결 후 세션 수가 올라야 한다")
                .isEqualTo(before + 1);

        session.disconnect();
        Thread.sleep(600);
        assertThat(metric(scrape(), "chat_sessions_active"))
                .as("끊으면 되돌아와야 한다. 안 줄면 붕괴 4단계 측정이 부풀려진다")
                .isEqualTo(before);
    }

    @Test
    @Order(3)
    @DisplayName("★ fan-out 배수가 기록된다 - 발행량만 세면 30명 방과 3000명 방이 같아 보인다")
    void fanout_records_recipient_count() throws Exception {
        StompSession session = connect();
        CompletableFuture<ChatMessageResponse> received = new CompletableFuture<>();
        session.subscribe("/topic/rooms/" + meetingId, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders h) { return ChatMessageResponse.class; }
            @Override public void handleFrame(StompHeaders h, Object p) {
                received.complete((ChatMessageResponse) p);
            }
        });
        Thread.sleep(400);

        assertThat(metric(scrape(), "chat_rooms_active"))
                .as("구독한 방이 하나 잡혀야 한다").isEqualTo(1.0);

        double publishedBefore = orZero(metric(scrape(), "chat_messages_published_total"));
        session.send("/app/rooms/" + meetingId + "/send", new ChatSendRequest("측정"));
        received.get(5, TimeUnit.SECONDS);
        Thread.sleep(300);

        String body = scrape();
        assertThat(metric(body, "chat_messages_published_total")).isEqualTo(publishedBefore + 1);
        assertThat(body)
                .as("수신자 수 분포가 기록되어야 한다")
                .contains("chat_fanout_recipients");
        assertThat(orZero(metric(body, "chat_fanout_recipients_sum")))
                .as("구독자 1명에게 갔으므로 합이 1 이상이어야 한다")
                .isGreaterThanOrEqualTo(1.0);

        // publishPercentileHistogram() 이 빠지면 _bucket 이 안 나오고
        // 대시보드의 histogram_quantile 패널이 통째로 "No data" 가 된다. (#28 의 5xx 패널과 같은 함정)
        assertThat(body)
                .as("histogram_quantile 을 쓰려면 버킷이 있어야 한다")
                .contains("chat_fanout_recipients_bucket");

        session.disconnect();
        Thread.sleep(600);
        assertThat(orZero(metric(scrape(), "chat_rooms_active")))
                .as("세션이 끊기면 UNSUBSCRIBE 없이 사라진다. 정리하지 않으면 방이 영영 남는다")
                .isZero();
    }

    private double orZero(Double v) {
        return v == null ? 0.0 : v;
    }
}
