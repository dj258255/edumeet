package com.edu.edumeet.integration.chat;

import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.config.jwt.JwtService;
import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.domain.MeetingParticipant;
import com.edu.edumeet.meeting.domain.SessionType;
import com.edu.edumeet.member.domain.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 서버가 STOMP 하트비트를 실제로 보내는가. (#106)
 *
 * <p><b>왜 이 시험이 필요한가.</b>
 * {@code setHeartbeatValue} 만 주고 {@code setTaskScheduler} 를 빠뜨리면
 * Spring 은 <b>조용히 하트비트를 보내지 않는다.</b> 예외도 경고도 없다.
 * 설정 파일만 봐서는 켜져 있는 것으로 보인다 -
 * 이 저장소에서 여러 번 본 "설정은 있는데 아무것도 안 하는" 모양이다.
 *
 * <p>그래서 <b>CONNECTED 프레임이 실제로 합의한 값</b>을 본다.
 * 서버가 하트비트를 끄면 그 헤더가 {@code 0,0} 으로 온다.
 *
 * <p><b>왜 하트비트가 필요한가.</b> 조용한 연결 3개를 90초 유지했더니
 * 프록시가 <b>60.9초에 전부 끊었다</b>({@code proxy_read_timeout} 기본 60초).
 * 하트비트가 있으면 유휴가 아니게 되므로 타임아웃을 짧게 둘 수 있고,
 * 죽은 연결을 빨리 걷어낼 수 있다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("STOMP 하트비트")
class StompHeartbeatTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    /** nginx proxy_read_timeout 기본값. 하트비트는 이보다 충분히 짧아야 한다. */
    private static final long PROXY_DEFAULT_TIMEOUT_MS = 60_000L;

    /** STOMP 프레임 구분자. 줄바꿈은 LF 고 프레임 끝은 널 바이트(0x00)다. */
    private static final String LF = "\n";
    private static final String NUL = String.valueOf((char) 0);

    @LocalServerPort int port;
    @Autowired JwtService jwtService;
    @Autowired TransactionTemplate transactionTemplate;
    @PersistenceContext EntityManager em;

    private record Fixture(Long meetingId, String token) {}

    private Fixture given() {
        String email = "hb-" + SEQ.incrementAndGet() + "@test";
        Long[] id = new Long[1];
        transactionTemplate.executeWithoutResult(s -> {
            Member owner = Member.builder().email(email).nickname("참가자").password("x").build();
            em.persist(owner);
            ClassRoom c = ClassRoom.builder().member(owner).title("클래스").description("-")
                    .participantLimit(30).isDeleted(false).build();
            em.persist(c);
            Meeting m = Meeting.builder().classRoom(c).title("세션").description("-")
                    .sessionType(SessionType.INTERACTIVE).startTime(LocalDateTime.now()).build();
            em.persist(m);
            em.persist(MeetingParticipant.join(m, email));
            em.flush();
            id[0] = m.getId();
        });
        return new Fixture(id[0], jwtService.generateAccessToken(1L, email));
    }

    /**
     * 원시 WebSocket 으로 CONNECTED 프레임을 그대로 받아 온다.
     *
     * <p>STOMP 클라이언트 라이브러리를 쓰지 않는 이유 - 라이브러리가 하트비트 협상을
     * 대신 처리하고 프레임을 감춰 버린다. 우리가 보려는 것이 바로 그 프레임이다.
     *
     * @param wanted 클라이언트가 받고 싶다고 알리는 주기(ms). 서버는 자기 설정과 이 값 중
     *               <b>큰 쪽</b>을 고른다.
     */
    private String connectedFrameWith(long wanted) throws Exception {
        Fixture f = given();
        List<String> frames = new CopyOnWriteArrayList<>();
        CountDownLatch connected = new CountDownLatch(1);

        WebSocketHandler handler = new AbstractWebSocketHandler() {
            @Override
            public void handleTextMessage(WebSocketSession session, TextMessage message) {
                String payload = message.getPayload();
                frames.add(payload);
                if (payload.startsWith("CONNECTED")) {
                    connected.countDown();
                }
            }
        };

        WebSocketSession session = new StandardWebSocketClient()
                .execute(handler, "ws://localhost:" + port + "/ws").get(5, TimeUnit.SECONDS);

        // heart-beat: <내가 보낼 주기>,<내가 받고 싶은 주기>
        //
        // ★ 프레임은 널 바이트로 끝나야 한다. 공백이나 개행으로 끝내면
        //   서버가 프레임이 안 끝났다고 보고 계속 기다린다 - 응답이 아예 안 온다.
        //   에러도 안 나므로 "연결은 됐는데 아무것도 안 온다" 로만 보인다.
        String connect = "CONNECT" + LF
                + "accept-version:1.2" + LF
                + "heart-beat:0," + wanted + LF
                + "host:localhost" + LF
                + "Authorization:Bearer " + f.token() + LF
                + LF + NUL;
        session.sendMessage(new TextMessage(connect));

        assertThat(connected.await(5, TimeUnit.SECONDS))
                .as("CONNECTED 프레임이 안 왔다. 인증이 막혔거나 엔드포인트가 다르다. 받은 것: %s", frames)
                .isTrue();
        session.close();

        return frames.stream().filter(x -> x.startsWith("CONNECTED")).findFirst().orElseThrow();
    }

    /** CONNECTED 의 heart-beat 헤더에서 "서버가 보낼 주기" 를 꺼낸다. */
    private long serverSendInterval(String connectedFrame) {
        return connectedFrame.lines()
                .filter(l -> l.startsWith("heart-beat:"))
                .map(l -> l.substring("heart-beat:".length()).split(",")[0].trim())
                .map(Long::parseLong)
                .findFirst()
                .orElse(0L);
    }

    @Test
    @DisplayName("★ 서버가 하트비트를 켠다 - setTaskScheduler 를 빠뜨리면 조용히 꺼진다")
    void server_negotiates_a_heartbeat() throws Exception {
        String frame = connectedFrameWith(10_000L);

        assertThat(frame)
                .as("CONNECTED 에 heart-beat 헤더가 없다. 실제 프레임: %s",
                        frame.replace("\n", "\\n"))
                .contains("heart-beat:");

        assertThat(serverSendInterval(frame))
                .as("""
                    서버가 보낼 주기가 0 이다 = 하트비트가 꺼져 있다.
                    setHeartbeatValue 를 줬어도 setTaskScheduler 가 없으면
                    Spring 은 조용히 끈다 - 예외도 경고도 없다.""")
                .isGreaterThan(0);
    }

    @Test
    @DisplayName("★ 합의된 주기가 프록시 타임아웃의 절반보다 짧다 - 한 번 놓쳐도 살아남게")
    void interval_survives_one_missed_beat() throws Exception {
        long interval = serverSendInterval(connectedFrameWith(10_000L));

        assertThat(interval)
                .as("""
                    서버가 보낼 주기가 %dms 다.
                    nginx proxy_read_timeout 기본값(%dms)의 절반을 넘으면
                    하트비트를 한 번만 놓쳐도 프록시가 연결을 끊는다.""",
                        interval, PROXY_DEFAULT_TIMEOUT_MS)
                .isLessThanOrEqualTo(PROXY_DEFAULT_TIMEOUT_MS / 2);
    }

    @Test
    @DisplayName("클라이언트가 더 긴 주기를 원하면 그쪽을 따른다 - STOMP 협상 규칙")
    void longer_client_request_wins() throws Exception {
        // STOMP 1.2: 서버가 보낼 주기 = max(서버 설정, 클라이언트가 원하는 값)
        // 클라이언트가 감당 못 하는 속도로 밀어 넣지 않기 위한 규칙이다.
        long withShort = serverSendInterval(connectedFrameWith(1_000L));
        long withLong = serverSendInterval(connectedFrameWith(40_000L));

        assertThat(withLong)
                .as("클라이언트가 40초를 원했는데 서버가 더 자주 보낸다면 협상이 깨진 것이다")
                .isGreaterThanOrEqualTo(withShort);
    }
}
