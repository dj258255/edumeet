package com.edu.edumeet.integration.chat;

import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.config.jwt.JwtService;
import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.domain.MeetingParticipant;
import com.edu.edumeet.meeting.domain.SessionType;
import com.edu.edumeet.meeting.dto.CaptionBroadcast;
import com.edu.edumeet.meeting.dto.CaptionIngestRequest;
import com.edu.edumeet.meeting.service.CaptionReplayBuffer;
import com.edu.edumeet.meeting.service.CaptionService;
import com.edu.edumeet.member.domain.Member;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 끊겼다 붙은 사람이 그 사이 자막을 받는지 고정한다. (#165)
 *
 * <h3>왜 이 시험이 생겼나</h3>
 * 재접속을 재 봤더니 <b>150/150 성공, p95 292ms</b> 로 깔끔했다.
 * 그런데 같은 회차에서 <b>9,475건을 놓쳤다</b>(재접속당 63건).
 * {@code 끊어 둔 3초 x 발행 20/s = 60건} 과 맞으니 일부가 아니라 전부였다. (#164)
 *
 * <p><b>접속 수만 세면 이 구멍이 안 보인다.</b> 재접속률 100% 와 자막 유실이
 * 같은 회차의 결과다. 그래서 "다시 붙었나" 가 아니라 <b>"그 사이 것을 받았나"</b> 를 고정한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("자막 재접속 복구")
class CaptionReplayTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @LocalServerPort int port;
    @Autowired JwtService jwtService;
    @Autowired CaptionService captionService;
    @Autowired CaptionReplayBuffer replayBuffer;
    @Autowired TransactionTemplate transactionTemplate;
    @PersistenceContext EntityManager em;

    private WebSocketStompClient stompClient;
    private Long meetingId;
    private String token;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        String email = "caption-replay-" + SEQ.incrementAndGet() + "@test";

        transactionTemplate.executeWithoutResult(s -> {
            Member owner = Member.builder().email(email).nickname("참가자").password("x").build();
            em.persist(owner);
            ClassRoom room = ClassRoom.builder().member(owner).title("클래스").description("-")
                    .participantLimit(30).isDeleted(false).build();
            em.persist(room);
            Meeting meeting = Meeting.builder().classRoom(room).title("회의").description("-")
                    .sessionType(SessionType.BROADCAST)
                    .startTime(LocalDateTime.now().minusMinutes(10)).build();
            em.persist(meeting);
            em.persist(MeetingParticipant.join(meeting, email));
            em.flush();
            meetingId = meeting.getId();
        });
        token = jwtService.generateAccessToken(1L, email);
    }

    @AfterEach
    void tearDown() {
        if (meetingId != null) replayBuffer.forget(meetingId);
        if (stompClient != null) stompClient.stop();
        // ★ 이 시험이 만든 회의만 지운다. 조건 없는 DELETE 는 같은 컨텍스트를
        //   쓰는 다른 시험의 회의까지 지워, 엉뚱한 시험을 실행마다 다르게 깬다. (#172)
        if (meetingId == null) return;
        Long id = meetingId;
        transactionTemplate.executeWithoutResult(s -> {
            // 자막은 비동기로 저장되므로 회의보다 먼저 지워야 FK 가 안 걸린다.
            em.createQuery("DELETE FROM CaptionSegment c WHERE c.meeting.id = :id")
                    .setParameter("id", id).executeUpdate();
            em.createQuery("DELETE FROM MeetingParticipant p WHERE p.meeting.id = :id")
                    .setParameter("id", id).executeUpdate();
            em.createQuery("DELETE FROM Meeting m WHERE m.id = :id")
                    .setParameter("id", id).executeUpdate();
        });
    }

    @Test
    @DisplayName("끊긴 사이에 나온 자막을 다시 붙을 때 받는다")
    void 끊긴_사이_자막을_다시_받는다() throws Exception {
        // 아무도 안 듣는 동안 자막이 세 건 나갔다. 끊겨 있던 구간이다.
        broadcast("첫째", 1L);
        broadcast("둘째", 2L);
        broadcast("셋째", 3L);

        List<CaptionBroadcast> got = subscribeAndCollect();

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(got)
                        .as("끊긴 동안 나온 셋을 다시 붙을 때 받아야 한다")
                        .hasSize(3));
        assertThat(got).extracting(CaptionBroadcast::text)
                .containsExactly("첫째", "둘째", "셋째");
    }

    @Test
    @DisplayName("복구본은 replay 로 표시돼 온다")
    void 복구본은_표시된다() throws Exception {
        broadcast("지나간 자막", 1L);

        List<CaptionBroadcast> got = subscribeAndCollect();
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(got).hasSize(1));

        assertThat(got.get(0).replay())
                .as("표시가 없으면 받는 쪽이 실시간과 복구본을 구분할 방법이 없다. "
                        + "복구본을 실시간 자막 줄에 띄우면 지금 말로 오해한다")
                .isTrue();
    }

    @Test
    @DisplayName("중간 자막은 복구하지 않는다")
    void 중간_자막은_복구하지_않는다() throws Exception {
        broadcastInterim("말하는 중", 1L);
        broadcast("확정된 말", 2L);

        List<CaptionBroadcast> got = subscribeAndCollect();
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(got).hasSize(1));

        assertThat(got).extracting(CaptionBroadcast::text)
                .as("중간 자막은 지나가면 값이 없다. 들고 있으면 버퍼만 커진다")
                .containsExactly("확정된 말");
    }

    @Test
    @DisplayName("회의가 끝나면 버퍼를 비운다")
    void 회의가_끝나면_버퍼를_비운다() {
        broadcast("자막", 1L);
        assertThat(replayBuffer.recent(meetingId)).hasSize(1);

        replayBuffer.forget(meetingId);

        assertThat(replayBuffer.recent(meetingId))
                .as("안 비우면 방이 늘수록 힙에 쌓인다")
                .isEmpty();
    }

    @Test
    @DisplayName("상한을 넘으면 오래된 것부터 버린다")
    void 상한을_넘으면_오래된_것부터_버린다() {
        int limit = 60;
        for (int i = 1; i <= limit + 5; i++) broadcast("자막 " + i, (long) i);

        List<CaptionBroadcast> kept = replayBuffer.recent(meetingId);

        assertThat(kept).hasSize(limit);
        assertThat(kept.get(0).text())
                .as("복구는 최근 구간을 메우는 것이지 강의 전체를 다시 주는 것이 아니다")
                .isEqualTo("자막 6");
    }

    // ── 도우미 ──────────────────────────────────────────────

    private void broadcast(String text, Long sequence) {
        captionService.broadcast(meetingId,
                new CaptionIngestRequest(text, sequence, System.currentTimeMillis(), true),
                System.currentTimeMillis());
    }

    private void broadcastInterim(String text, Long sequence) {
        captionService.broadcast(meetingId,
                new CaptionIngestRequest(text, sequence, System.currentTimeMillis(), false),
                System.currentTimeMillis());
    }

    /** 구독하고, 이 연결로만 오는 복구본을 모은다. */
    private List<CaptionBroadcast> subscribeAndCollect() throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);
        StompSession session = stompClient.connectAsync(
                        "ws://localhost:" + port + "/ws",
                        new WebSocketHttpHeaders(), connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        // 놓친 구간은 전용 목적지로 한 프레임에 온다. 여러 개로 나누면 순서가 안 지켜진다.
        List<CaptionBroadcast> got = new CopyOnWriteArrayList<>();
        session.subscribe("/topic/rooms/" + meetingId + "/captions/gap", new StompFrameHandler() {
            // 배열로 오므로 CaptionBroadcast[] 로 받는다. List 제네릭은 컨버터가 못 푼다.
            @Override public Type getPayloadType(StompHeaders h) { return CaptionBroadcast[].class; }
            @Override public void handleFrame(StompHeaders h, Object payload) {
                got.addAll(java.util.Arrays.asList((CaptionBroadcast[]) payload));
            }
        });
        return got;
    }
}
