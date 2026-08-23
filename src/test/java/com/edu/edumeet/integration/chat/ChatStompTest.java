package com.edu.edumeet.integration.chat;

import com.edu.edumeet.chat.dto.ChatMessageResponse;
import com.edu.edumeet.chat.dto.ChatSendRequest;
import com.edu.edumeet.chat.repository.ChatMessageRepository;
import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.config.jwt.JwtService;
import com.edu.edumeet.member.domain.Member;
import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.domain.MeetingParticipant;
import com.edu.edumeet.meeting.domain.SessionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 채팅 STOMP 경로를 실제 WebSocket 연결로 검증한다. (#33)
 *
 * <p><b>왜 실제 연결인가</b> — Spring Security 의 HTTP 필터 체인은
 * <b>핸드셰이크까지만</b> 관여하고 그 뒤 STOMP 프레임에는 관여하지 않는다.
 * MockMvc 로는 이 경계 자체를 재현할 수 없다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("채팅 STOMP")
class ChatStompTest {

    @LocalServerPort int port;

    @Autowired JwtService jwtService;
    @Autowired ChatMessageRepository chatMessageRepository;
    @Autowired TransactionTemplate transactionTemplate;
    @PersistenceContext EntityManager em;

    private WebSocketStompClient stompClient;
    private Long interactiveMeetingId;
    private Long broadcastMeetingId;
    private String memberToken;
    private String outsiderToken;

    /** 테스트마다 새 이메일을 쓴다. 정리 순서를 맞추려 FK 를 타고 지우는 것보다 단순하다. */
    private static final java.util.concurrent.atomic.AtomicInteger SEQ =
            new java.util.concurrent.atomic.AtomicInteger();

    private String member;
    private String outsider;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        int n = SEQ.incrementAndGet();
        member = "chat-member-" + n + "@test";
        outsider = "chat-outsider-" + n + "@test";

        transactionTemplate.executeWithoutResult(status -> {
            Member owner = Member.builder().email(member).nickname("참가자").password("x").build();
            em.persist(owner);
            Member other = Member.builder().email(outsider).nickname("외부인").password("x").build();
            em.persist(other);

            ClassRoom classRoom = ClassRoom.builder()
                    .member(owner).title("클래스").description("-")
                    .participantLimit(30).isDeleted(false).build();
            em.persist(classRoom);

            interactiveMeetingId = persistMeeting(classRoom, SessionType.INTERACTIVE, member);
            broadcastMeetingId = persistMeeting(classRoom, SessionType.BROADCAST, member);
        });

        memberToken = jwtService.generateAccessToken(1L, member);
        outsiderToken = jwtService.generateAccessToken(2L, outsider);
    }

    private Long persistMeeting(ClassRoom classRoom, SessionType type, String participantEmail) {
        Meeting meeting = Meeting.builder()
                .classRoom(classRoom).title("회의 " + type).description("-")
                .sessionType(type).startTime(LocalDateTime.now().minusMinutes(10))
                .build();
        em.persist(meeting);
        em.persist(MeetingParticipant.join(meeting, participantEmail));
        em.flush();
        return meeting.getId();
    }

    @AfterEach
    void tearDown() {
        if (stompClient != null) {
            stompClient.stop();
        }
        transactionTemplate.executeWithoutResult(status -> {
            em.createQuery("DELETE FROM ChatMessage").executeUpdate();
            em.createQuery("DELETE FROM MeetingParticipant").executeUpdate();
            em.createQuery("DELETE FROM Meeting").executeUpdate();
        });
    }

    private StompSession connect(String token) throws Exception {
        return connect(token, new StompSessionHandlerAdapter() {});
    }

    /**
     * 세션 단위 오류(ERROR 프레임, 전송 오류)는 <b>connect() 에 넘긴 핸들러</b>가 받는다.
     * subscribe() 에 넘기는 것은 프레임 핸들러라서 여기로 오지 않는다.
     */
    private StompSession connect(String token, StompSessionHandler handler) throws Exception {
        StompHeaders headers = new StompHeaders();
        if (token != null) {
            headers.add("Authorization", "Bearer " + token);
        }
        return stompClient.connectAsync("ws://localhost:" + port + "/ws",
                        new WebSocketHttpHeaders(), headers, handler)
                .get(5, TimeUnit.SECONDS);
    }

    /** 브로드캐스트를 한 건 기다린다. */
    private CompletableFuture<ChatMessageResponse> subscribe(StompSession session, Long meetingId) {
        CompletableFuture<ChatMessageResponse> received = new CompletableFuture<>();
        session.subscribe("/topic/rooms/" + meetingId, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return ChatMessageResponse.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                received.complete((ChatMessageResponse) payload);
            }
        });
        return received;
    }

    // ── 인증 ────────────────────────────────────────────────

    @Test
    @DisplayName("토큰 없이 CONNECT 하면 거절된다")
    void 토큰_없으면_연결_거절() {
        assertThatThrownBy(() -> connect(null))
                .as("HTTP 필터는 핸드셰이크만 본다. 프레임 단계에서 막아야 한다")
                .isInstanceOf(ExecutionException.class);
    }

    @Test
    @DisplayName("잘못된 토큰이면 거절된다")
    void 잘못된_토큰이면_연결_거절() {
        assertThatThrownBy(() -> connect("not-a-real-token"))
                .isInstanceOf(ExecutionException.class);
    }

    @Test
    @DisplayName("유효한 토큰이면 연결된다")
    void 유효한_토큰이면_연결된다() throws Exception {
        StompSession session = connect(memberToken);
        assertThat(session.isConnected()).isTrue();
        session.disconnect();
    }

    // ── 구독 권한 ────────────────────────────────────────────

    @Test
    @DisplayName("★ 참가하지 않은 방은 구독할 수 없다")
    void 참가하지_않은_방은_구독_불가() throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch rejected = new CountDownLatch(1);

        StompSession session = connect(outsiderToken, new StompSessionHandlerAdapter() {
            @Override public void handleTransportError(StompSession s, Throwable ex) {
                error.set(ex); rejected.countDown();
            }
            @Override public void handleException(StompSession s, StompCommand c,
                                                  StompHeaders h, byte[] p, Throwable ex) {
                error.set(ex); rejected.countDown();
            }
        });

        session.subscribe("/topic/rooms/" + interactiveMeetingId, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return ChatMessageResponse.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                error.set(new AssertionError("메시지가 도착하면 안 된다: " + payload));
                rejected.countDown();
            }
        });

        assertThat(rejected.await(5, TimeUnit.SECONDS))
                .as("구독을 막지 않으면 남의 회의 채팅을 그대로 받아볼 수 있다")
                .isTrue();
        assertThat(error.get())
                .as("도착한 것은 메시지가 아니라 거절이어야 한다")
                .isNotInstanceOf(AssertionError.class);
    }

    // ── 브로드캐스트 ─────────────────────────────────────────

    @Test
    @DisplayName("보낸 메시지가 방으로 브로드캐스트된다")
    void 메시지가_방으로_브로드캐스트된다() throws Exception {
        StompSession session = connect(memberToken);
        CompletableFuture<ChatMessageResponse> received = subscribe(session, interactiveMeetingId);
        Thread.sleep(300);   // 구독 등록이 브로커에 반영될 시간

        long before = System.currentTimeMillis();
        session.send("/app/rooms/" + interactiveMeetingId + "/send", new ChatSendRequest("안녕하세요"));

        ChatMessageResponse response = received.get(5, TimeUnit.SECONDS);
        assertThat(response.content()).isEqualTo("안녕하세요");
        assertThat(response.sender())
                .as("보낸 사람은 페이로드가 아니라 JWT 에서 나와야 한다")
                .isEqualTo(member);
        assertThat(response.publishedAt())
                .as("k6 가 이 값으로 end-to-end 지연을 잰다")
                .isGreaterThanOrEqualTo(before);
        session.disconnect();
    }

    // ── 저장 정책 ────────────────────────────────────────────

    @Test
    @DisplayName("INTERACTIVE 는 저장한다")
    void INTERACTIVE_는_저장한다() throws Exception {
        StompSession session = connect(memberToken);
        CompletableFuture<ChatMessageResponse> received = subscribe(session, interactiveMeetingId);
        Thread.sleep(300);
        session.send("/app/rooms/" + interactiveMeetingId + "/send", new ChatSendRequest("수업 기록"));
        received.get(5, TimeUnit.SECONDS);

        assertThat(chatMessageRepository.countByMeetingId(interactiveMeetingId)).isEqualTo(1);
        session.disconnect();
    }

    @Test
    @DisplayName("★ BROADCAST 는 저장하지 않는다 - 저장하면 브로드캐스트 측정이 DB 쓰기에 묻힌다")
    void BROADCAST_는_저장하지_않는다() throws Exception {
        StompSession session = connect(memberToken);
        CompletableFuture<ChatMessageResponse> received = subscribe(session, broadcastMeetingId);
        Thread.sleep(300);
        session.send("/app/rooms/" + broadcastMeetingId + "/send", new ChatSendRequest("방송 채팅"));

        assertThat(received.get(5, TimeUnit.SECONDS).content()).isEqualTo("방송 채팅");
        assertThat(chatMessageRepository.countByMeetingId(broadcastMeetingId))
                .as("시청자 수천 명 x 초당 수십 메시지면 쓰기가 폭증한다")
                .isZero();
        session.disconnect();
    }
}
