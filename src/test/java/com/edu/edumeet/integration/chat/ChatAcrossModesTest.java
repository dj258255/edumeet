package com.edu.edumeet.integration.chat;

import com.edu.edumeet.chat.dto.ChatMessageResponse;
import com.edu.edumeet.chat.dto.ChatSendRequest;
import com.edu.edumeet.chat.repository.ChatMessageRepository;
import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.config.jwt.JwtService;
import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.domain.MeetingParticipant;
import com.edu.edumeet.meeting.domain.SessionType;
import com.edu.edumeet.member.domain.Member;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 채팅은 방송 모드 세 개에 공통이다. (#71)
 *
 * <pre>
 *   INTERACTIVE       화상채팅
 *   BROADCAST         라이브 스트리밍
 *   AUDIO_BROADCAST   오디오 스트리밍
 * </pre>
 *
 * <p><b>접근 정책과 저장 정책이 분리되어 있다.</b>
 * "누가 볼 수 있나" 는 방 참가 여부이고, "저장하나" 는 모드 특성이다.
 * 이 둘이 섞이면 <b>모드가 늘 때마다 접근 로직을 건드리게 된다.</b>
 *
 * <p>{@code @EnumSource} 를 쓴 이유 — <b>모드를 추가하면 이 테스트가 그 모드에 대해서도 돈다.</b>
 * 새 모드에서 채팅이 안 되는 것을 잊고 넘어갈 수 없다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("채팅은 모든 방송 모드에서 동작한다")
class ChatAcrossModesTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @LocalServerPort int port;
    @Autowired JwtService jwtService;
    @Autowired ChatMessageRepository chatMessageRepository;
    @Autowired TransactionTemplate transactionTemplate;
    @PersistenceContext EntityManager em;

    private WebSocketStompClient stompClient;

    @AfterEach
    void tearDown() {
        if (stompClient != null) stompClient.stop();
    }

    private record Fixture(Long meetingId, String token) {}

    private Fixture given(SessionType type) {
        String email = "modes-" + SEQ.incrementAndGet() + "@test";
        Long[] id = new Long[1];
        transactionTemplate.executeWithoutResult(status -> {
            Member owner = Member.builder().email(email).nickname("참가자").password("x").build();
            em.persist(owner);
            ClassRoom classRoom = ClassRoom.builder()
                    .member(owner).title("클래스").description("-")
                    .participantLimit(30).isDeleted(false).build();
            em.persist(classRoom);
            Meeting meeting = Meeting.builder()
                    .classRoom(classRoom).title("세션 " + type).description("-")
                    .sessionType(type).startTime(LocalDateTime.now()).build();
            em.persist(meeting);
            em.persist(MeetingParticipant.join(meeting, email));
            em.flush();
            id[0] = meeting.getId();
        });
        return new Fixture(id[0], jwtService.generateAccessToken(1L, email));
    }

    private StompSession connect(String token) throws Exception {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + token);
        return stompClient.connectAsync("ws://localhost:" + port + "/ws",
                        new WebSocketHttpHeaders(), headers, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
    }

    @ParameterizedTest
    @EnumSource(SessionType.class)
    @DisplayName("★ 모드와 무관하게 채팅이 오간다")
    void chat_works_in_every_mode(SessionType type) throws Exception {
        Fixture fixture = given(type);
        StompSession session = connect(fixture.token());

        CompletableFuture<ChatMessageResponse> received = new CompletableFuture<>();
        session.subscribe("/topic/rooms/" + fixture.meetingId(), new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders h) { return ChatMessageResponse.class; }
            @Override public void handleFrame(StompHeaders h, Object p) {
                received.complete((ChatMessageResponse) p);
            }
        });
        Thread.sleep(400);

        session.send("/app/rooms/" + fixture.meetingId() + "/send", new ChatSendRequest(type + " 에서 안녕"));

        assertThat(received.get(5, TimeUnit.SECONDS).content())
                .as("%s 에서 채팅이 안 되면 '공통 기능' 이라는 주장이 거짓이다", type)
                .isEqualTo(type + " 에서 안녕");
        session.disconnect();
    }

    @ParameterizedTest
    @EnumSource(SessionType.class)
    @DisplayName("★ 저장 여부만 모드에 따라 갈린다 - 접근이 아니라 저장이다")
    void only_persistence_differs_by_mode(SessionType type) throws Exception {
        Fixture fixture = given(type);
        StompSession session = connect(fixture.token());

        CompletableFuture<ChatMessageResponse> received = new CompletableFuture<>();
        session.subscribe("/topic/rooms/" + fixture.meetingId(), new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders h) { return ChatMessageResponse.class; }
            @Override public void handleFrame(StompHeaders h, Object p) {
                received.complete((ChatMessageResponse) p);
            }
        });
        Thread.sleep(400);

        session.send("/app/rooms/" + fixture.meetingId() + "/send", new ChatSendRequest("저장 확인"));
        received.get(5, TimeUnit.SECONDS);
        Thread.sleep(200);

        long stored = chatMessageRepository.countByMeetingId(fixture.meetingId());
        if (type.persistsChatInline()) {
            assertThat(stored).as("%s 는 수업 기록이라 저장한다", type).isEqualTo(1);
        } else {
            assertThat(stored)
                    .as("%s 는 발행 경로에서 저장하지 않는다. 다시보기용 비동기 저장은 #61", type)
                    .isZero();
        }
        session.disconnect();
    }
}
