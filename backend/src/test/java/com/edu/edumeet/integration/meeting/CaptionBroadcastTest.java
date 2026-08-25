package com.edu.edumeet.integration.meeting;

import com.edu.edumeet.chat.dto.ChatMessageResponse;
import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.config.internal.InternalApiTokenFilter;
import com.edu.edumeet.config.jwt.JwtService;
import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.domain.MeetingParticipant;
import com.edu.edumeet.meeting.domain.SessionType;
import com.edu.edumeet.meeting.dto.CaptionBroadcast;
import com.edu.edumeet.meeting.dto.CaptionTranscriptResponse;
import com.edu.edumeet.meeting.repository.CaptionSegmentRepository;
import com.edu.edumeet.meeting.service.CaptionArchiveQueue;
import com.edu.edumeet.member.domain.Member;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
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
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실시간 자막 브로드캐스트. (#65)
 *
 * <p><b>파이썬이 만들고 자바가 뿌린다.</b> 파이썬이 시청자에게 직접 뿌리지 않는 이유는
 * 인가·연결 수·fan-out 인프라가 전부 자바에 있기 때문이다.
 *
 * <p>여기서는 <b>자바 쪽 경계까지</b>를 검증한다 — 파이썬 저장소가 이 리포에 없어도
 * 내부 API 로 들어와 방으로 나가는 경로는 전부 확인할 수 있다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "edumeet.caption.archive.flush-interval-ms=600000")
@ActiveProfiles("test")
@DisplayName("실시간 자막")
class CaptionBroadcastTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final String TOKEN = "test-internal-token";

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired JwtService jwtService;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired CaptionArchiveQueue captionArchiveQueue;
    @Autowired CaptionSegmentRepository captionSegmentRepository;
    @PersistenceContext EntityManager em;

    private WebSocketStompClient stompClient;
    private Long meetingId;
    private String userToken;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        String email = "caption-" + SEQ.incrementAndGet() + "@test";
        transactionTemplate.executeWithoutResult(status -> {
            Member owner = Member.builder().email(email).nickname("청취자").password("x").build();
            em.persist(owner);
            ClassRoom classRoom = ClassRoom.builder()
                    .member(owner).title("클래스").description("-")
                    .participantLimit(30).isDeleted(false).build();
            em.persist(classRoom);
            Meeting meeting = Meeting.builder()
                    .classRoom(classRoom).title("오디오 방송").description("-")
                    .sessionType(SessionType.AUDIO_BROADCAST)
                    .startTime(LocalDateTime.now()).build();
            em.persist(meeting);
            em.persist(MeetingParticipant.join(meeting, email));
            em.flush();
            meetingId = meeting.getId();
        });
        userToken = jwtService.generateAccessToken(1L, email);
    }

    @AfterEach
    void tearDown() {
        if (stompClient != null) stompClient.stop();
        for (int i = 0; i < 100 && captionArchiveQueue.queuedCount() > 0; i++) {
            captionArchiveQueue.flush();
        }
    }

    private StompSession connect() throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + userToken);
        return stompClient.connectAsync("ws://localhost:" + port + "/ws",
                        new WebSocketHttpHeaders(), headers, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
    }

    private <T> CompletableFuture<T> subscribe(StompSession session, String destination, Class<T> type) {
        CompletableFuture<T> received = new CompletableFuture<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders h) { return type; }
            @Override public void handleFrame(StompHeaders h, Object p) { received.complete(type.cast(p)); }
        });
        return received;
    }

    private ResponseEntity<CaptionBroadcast> postCaption(String token, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.add(InternalApiTokenFilter.HEADER, token);
        return rest.postForEntity(
                "http://localhost:" + port + "/api/v1/internal/meetings/" + meetingId + "/captions",
                new HttpEntity<>(body, headers), CaptionBroadcast.class);
    }

    private ResponseEntity<CaptionTranscriptResponse> getTranscript(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.add(InternalApiTokenFilter.HEADER, token);
        return rest.exchange(
                "http://localhost:" + port + "/api/v1/internal/meetings/" + meetingId + "/captions/transcript",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                CaptionTranscriptResponse.class);
    }

    @Test
    @DisplayName("토큰 없이 보내면 401 이다")
    void requires_internal_token() {
        assertThat(postCaption(null, Map.of("text", "안녕하세요")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("★ 자막이 방으로 브로드캐스트된다")
    void caption_reaches_subscriber() throws Exception {
        StompSession session = connect();
        CompletableFuture<CaptionBroadcast> received =
                subscribe(session, "/topic/rooms/" + meetingId + "/captions", CaptionBroadcast.class);
        Thread.sleep(400);

        long spokenAt = System.currentTimeMillis() - 1500;   // STT 가 1.5초 전 발화를 인식했다고 가정
        postCaption(TOKEN, Map.of("text", "안녕하세요", "sequence", 42, "spokenAt", spokenAt));

        CaptionBroadcast caption = received.get(5, TimeUnit.SECONDS);
        assertThat(caption.text()).isEqualTo("안녕하세요");
        assertThat(caption.sequence())
                .as("순서가 없으면 클라이언트가 재정렬을 감지할 수 없다")
                .isEqualTo(42L);
        assertThat(caption.finalSegment()).isTrue();
        session.disconnect();
    }

    @Test
    @DisplayName("★ final 자막은 브로드캐스트 뒤 비동기 저장된다 - 화면 표시가 DB 를 기다리지 않는다")
    void final_caption_is_archived_after_broadcast() {
        postCaption(TOKEN, Map.of("text", "저장될 자막", "sequence", 1, "finalSegment", true));

        assertThat(captionArchiveQueue.queuedCount())
                .as("요청 반환 시점에 큐에 있어야 한다. DB 저장까지 기다리면 hot path 가 느려진다")
                .isEqualTo(1);
        assertThat(captionSegmentRepository.countByMeetingId(meetingId))
                .as("반환 시점에 이미 DB 에 있으면 동기 저장이다")
                .isZero();

        captionArchiveQueue.flush();

        assertThat(captionSegmentRepository.countByMeetingId(meetingId)).isEqualTo(1);
    }

    @Test
    @DisplayName("★ partial 자막은 저장하지 않는다 - 요약 토큰을 중간 결과에 쓰지 않는다")
    void partial_caption_is_not_archived() {
        postCaption(TOKEN, Map.of("text", "임시 자막", "sequence", 1, "finalSegment", false));

        assertThat(captionArchiveQueue.queuedCount()).isZero();
        captionArchiveQueue.flush();
        assertThat(captionSegmentRepository.countByMeetingId(meetingId)).isZero();
    }

    @Test
    @DisplayName("★ 저장된 final 자막으로 transcript 를 만든다 - sequence 순서와 중복 제거가 기준이다")
    void transcript_is_built_from_archived_final_captions() {
        postCaption(TOKEN, Map.of("text", "두 번째 문장", "sequence", 2, "finalSegment", true));
        postCaption(TOKEN, Map.of("text", "첫 번째 문장", "sequence", 1, "finalSegment", true));
        postCaption(TOKEN, Map.of("text", "첫 번째 문장 재시도", "sequence", 1, "finalSegment", true));
        postCaption(TOKEN, Map.of("text", "임시 중간 결과", "sequence", 3, "finalSegment", false));

        captionArchiveQueue.flush();

        ResponseEntity<CaptionTranscriptResponse> response = getTranscript(TOKEN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().segmentCount()).isEqualTo(2);
        assertThat(response.getBody().text()).isEqualTo("첫 번째 문장\n두 번째 문장");
    }

    @Test
    @DisplayName("★ 시각 세 개가 담긴다 - STT 지연과 홉 비용을 나눠서 재야 한다")
    void carries_three_timestamps() throws Exception {
        StompSession session = connect();
        CompletableFuture<CaptionBroadcast> received =
                subscribe(session, "/topic/rooms/" + meetingId + "/captions", CaptionBroadcast.class);
        Thread.sleep(400);

        long spokenAt = System.currentTimeMillis() - 1500;
        postCaption(TOKEN, Map.of("text", "측정", "sequence", 1, "spokenAt", spokenAt));

        CaptionBroadcast caption = received.get(5, TimeUnit.SECONDS);
        assertThat(caption.spokenAt()).isEqualTo(spokenAt);
        assertThat(caption.receivedAt())
                .as("자바가 요청을 받은 시각. spokenAt 과의 차이가 STT 지연이다")
                .isGreaterThanOrEqualTo(spokenAt);
        assertThat(caption.publishedAt())
                .as("자바가 브로드캐스트한 시각. receivedAt 과의 차이가 홉 비용이다")
                .isGreaterThanOrEqualTo(caption.receivedAt());

        long hopCost = caption.publishedAt() - caption.receivedAt();
        assertThat(hopCost)
                .as("홉 비용이 STT 예산(1,500~5,000ms)에 비해 무시할 수준인지가 이 설계의 전제다")
                .isLessThan(1000L);
        session.disconnect();
    }

    @Test
    @DisplayName("★ 채팅 구독자는 자막을 받지 않는다 - 목적지가 나뉘어야 끌 수 있다")
    void chat_subscribers_do_not_receive_captions() throws Exception {
        StompSession session = connect();
        CompletableFuture<ChatMessageResponse> chatReceived =
                subscribe(session, "/topic/rooms/" + meetingId, ChatMessageResponse.class);
        Thread.sleep(400);

        postCaption(TOKEN, Map.of("text", "자막", "sequence", 1));

        assertThatTimeout(chatReceived);
        session.disconnect();
    }

    private void assertThatTimeout(CompletableFuture<?> future) {
        try {
            Object unexpected = future.get(1500, TimeUnit.MILLISECONDS);
            throw new AssertionError("채팅 구독자에게 자막이 갔다: " + unexpected);
        } catch (TimeoutException expected) {
            // 정상 - 채팅 목적지로는 자막이 오면 안 된다
        } catch (InterruptedException | ExecutionException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("빈 자막은 400 이다")
    void rejects_blank_caption() {
        assertThat(postCaption(TOKEN, Map.of("text", "   ")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
