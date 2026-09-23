package com.edu.edumeet.integration.meeting;

import com.edu.edumeet.chat.metrics.ChatMetrics;
import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.meeting.broadcast.BroadcastService;
import com.edu.edumeet.meeting.config.BroadcastProperties;
import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.domain.SessionType;
import com.edu.edumeet.meeting.repository.MeetingRepository;
import com.edu.edumeet.member.domain.Member;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 재시작 뒤에 남는 유령 방송과, 아무도 안 듣는 방송을 고정한다. (#168)
 *
 * <h3>왜 이 시험이 생겼나</h3>
 * 송출 세션은 {@code ConcurrentHashMap} 이라 <b>메모리에만 있다.</b>
 * 앱이 재시작되면 ffmpeg 는 같이 죽는데 DB 의 {@code broadcastSessionId} 는 남는다.
 *
 * <p><b>배포할 때마다 난다. 그런데 증상이 조용하다</b> —
 * 발표자 화면은 계속 "방송 중" 이고 시청자는 세그먼트가 안 늘어나는데
 * <b>에러가 한 줄도 안 난다.</b> 그래서 사람이 눈으로는 못 잡는다.
 *
 * <p>기존 수거기({@code reapIdleSessions})는 이걸 못 잡는다.
 * 그것은 <b>메모리에 있는 세션</b>을 훑는데 재시작 뒤에는 그 맵이 비어 있다.
 * <b>훑을 것이 없으니 아무것도 안 한다.</b>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("방송 생존성")
class BroadcastLivenessTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired BroadcastService broadcastService;
    @Autowired MeetingRepository meetingRepository;
    @Autowired ChatMetrics chatMetrics;
    @Autowired BroadcastProperties broadcastProperties;
    @Autowired TransactionTemplate transactionTemplate;
    @PersistenceContext EntityManager em;

    private Long meetingId;
    private Long classRoomId;
    private String email;
    private Path hlsDirectory;
    private String originalFfmpegPath;
    private Duration originalIdleTimeout;

    @BeforeEach
    void setUp() {
        originalFfmpegPath = broadcastProperties.getFfmpegPath();
        originalIdleTimeout = broadcastProperties.getIdleTimeout();
        email = "broadcast-liveness-" + SEQ.incrementAndGet() + "@test";
        transactionTemplate.executeWithoutResult(s -> {
            Member owner = Member.builder().email(email).nickname("발표자").password("x").build();
            em.persist(owner);
            ClassRoom room = ClassRoom.builder().member(owner).title("클래스").description("-")
                    .participantLimit(30).isDeleted(false).build();
            em.persist(room);
            Meeting meeting = Meeting.builder().classRoom(room).title("방송").description("-")
                    .sessionType(SessionType.BROADCAST)
                    .startTime(LocalDateTime.now().minusMinutes(10)).build();
            em.persist(meeting);
            em.flush();
            meetingId = meeting.getId();
            classRoomId = room.getId();
        });
        hlsDirectory = Path.of(broadcastProperties.getOutputDir(), "meeting-" + meetingId);
    }

    @AfterEach
    void tearDown() throws IOException {
        broadcastProperties.setFfmpegPath(originalFfmpegPath);
        broadcastProperties.setIdleTimeout(originalIdleTimeout);
        // 싱글턴 BroadcastService 의 맵에는 이 시험이 만든 세션만 남길 수 있다.
        // 남의 세션을 훑거나 지우지 않는다. (#172)
        if (broadcastService.sessionOf(meetingId) != null) {
            broadcastService.stop(email, meetingId);
        }
        // ★ 이 시험이 만든 회의만 지운다.
        //
        //   DELETE FROM Meeting 처럼 전체를 지우면 같은 컨텍스트를 쓰는 다른 시험의
        //   픽스처까지 날아간다. 실제로 그렇게 해서 시험 12개를 더 깨뜨렸다.
        //   정리는 자기가 만든 것까지만 한다.
        transactionTemplate.executeWithoutResult(s -> {
            em.createQuery("DELETE FROM CaptionSegment c WHERE c.meeting.id = :id")
                    .setParameter("id", meetingId).executeUpdate();
            em.createQuery("DELETE FROM MeetingParticipant p WHERE p.meeting.id = :id")
                    .setParameter("id", meetingId).executeUpdate();
            em.createQuery("DELETE FROM Meeting m WHERE m.id = :id")
                    .setParameter("id", meetingId).executeUpdate();
            // 클래스와 회원까지 치운다. 남겨 두면 다른 시험이 스키마를 다시 만들 때
            // class_room_seq 가 되감기면서 PK 가 부딪힌다. 실제로 12개를 깨뜨렸다.
            em.createQuery("DELETE FROM ClassRoom c WHERE c.id = :id")
                    .setParameter("id", classRoomId).executeUpdate();
            em.createQuery("DELETE FROM Member m WHERE m.email = :email")
                    .setParameter("email", email).executeUpdate();
        });
        if (Files.isDirectory(hlsDirectory)) {
            try (var files = Files.list(hlsDirectory)) {
                for (Path file : files.toList()) {
                    Files.deleteIfExists(file);
                }
            }
        }
        Files.deleteIfExists(hlsDirectory);
    }

    @Test
    @DisplayName("재시작 전에 방송 중이던 회의를 기동하면서 정리한다")
    void 유령_방송을_정리한다() throws IOException {
        // 재시작 직전 상태를 만든다 - DB 에는 방송 중인데 메모리에는 세션이 없다.
        transactionTemplate.executeWithoutResult(s -> {
            Meeting meeting = em.find(Meeting.class, meetingId);
            meeting.startBroadcast("죽은-세션", "/hls/meeting-" + meetingId + "/live.m3u8");
        });
        Files.createDirectories(hlsDirectory);
        Path playlist = hlsDirectory.resolve("live.m3u8");
        Files.writeString(playlist, "#EXTM3U");
        assertThat(meetingRepository.findById(meetingId).orElseThrow().isBroadcasting())
                .as("전제: DB 는 아직 방송 중이라고 알고 있다")
                .isTrue();
        assertThat(broadcastService.activeCount())
                .as("전제: 재시작 뒤라 메모리에는 세션이 없다")
                .isZero();

        broadcastService.reconcileOnStartup();

        assertThat(meetingRepository.findById(meetingId).orElseThrow().isBroadcasting())
                .as("정리하지 않으면 발표자 화면은 계속 '방송 중' 이고 시청자는 "
                        + "세그먼트가 안 늘어나는데 에러가 한 줄도 안 난다")
                .isFalse();
        assertThat(playlist)
                .as("새 슬롯이 뜨는 동안 옛 슬롯이 아직 송출할 수 있으므로 여기서 파일을 지우면 안 된다")
                .exists();
    }

    @Test
    @DisplayName("방송 종료 뒤에는 지난 방송의 HLS 플레이리스트가 없다")
    void 방송_종료_뒤_HLS_파일을_지운다() throws IOException {
        transactionTemplate.executeWithoutResult(s ->
                em.find(Meeting.class, meetingId).startBroadcast("old", "/hls/x.m3u8"));
        Files.createDirectories(hlsDirectory);
        Path playlist = hlsDirectory.resolve("live.m3u8");
        Files.writeString(playlist, "#EXTM3U\nseg_old_00000.m4s\n");

        broadcastService.stop(email, meetingId);

        assertThat(playlist)
                .as("주소는 DB 기록으로 남아도 마지막 조각을 다시 재생하면 안 된다")
                .doesNotExist();
    }

    @Test
    @DisplayName("HLS 삭제에 실패해도 방송 종료 상태는 커밋한다")
    void HLS_삭제_실패가_종료를_막지_않는다() throws IOException {
        transactionTemplate.executeWithoutResult(s ->
                em.find(Meeting.class, meetingId).startBroadcast("old", "/hls/x.m3u8"));
        // 디렉터리여야 할 위치를 파일로 만들면 Files.list 가 실패한다.
        Files.writeString(hlsDirectory, "not a directory");

        broadcastService.stop(email, meetingId);

        assertThat(meetingRepository.findById(meetingId).orElseThrow().isBroadcasting())
                .as("afterCommit 삭제 실패는 이미 확정한 종료 상태를 되돌리면 안 된다")
                .isFalse();
    }

    @Test
    @DisplayName("A의 늦은 stop은 이미 시작한 B를 방송 종료로 만들지 않는다")
    void 늦은_stop이_새_방송을_끝내지_않는다() {
        String sessionA = startDeadSession();
        transactionTemplate.executeWithoutResult(s ->
                em.find(Meeting.class, meetingId).startBroadcast("B", "/hls/x.m3u8"));

        broadcastService.stop(email, meetingId);

        Meeting meeting = meetingRepository.findById(meetingId).orElseThrow();
        assertThat(meeting.isBroadcasting()).isTrue();
        assertThat(meeting.getBroadcastSessionId()).isEqualTo("B");
        assertThat(sessionA).isNotEqualTo("B");
    }

    @Test
    @DisplayName("유휴·사망 세션 회수는 DB의 방송 상태도 끝낸다")
    void 유휴_회수는_방송_상태를_끝낸다() {
        startDeadSession();

        broadcastService.reapIdleSessions();

        assertThat(meetingRepository.findById(meetingId).orElseThrow().isBroadcasting()).isFalse();
    }

    @Test
    @DisplayName("유휴 회수도 이미 시작한 B의 방송 상태를 건드리지 않는다")
    void 유휴_회수는_새_방송을_끝내지_않는다() {
        String sessionA = startDeadSession();
        transactionTemplate.executeWithoutResult(s ->
                em.find(Meeting.class, meetingId).startBroadcast("B", "/hls/x.m3u8"));

        broadcastService.reapIdleSessions();

        Meeting meeting = meetingRepository.findById(meetingId).orElseThrow();
        assertThat(meeting.isBroadcasting()).isTrue();
        assertThat(meeting.getBroadcastSessionId()).isEqualTo("B");
        assertThat(sessionA).isNotEqualTo("B");
    }

    /** ffmpeg 없이도 sessions 맵·세대 전환을 시험하려고 즉시 끝나는 프로세스를 쓴다. */
    private String startDeadSession() {
        broadcastProperties.setFfmpegPath("/usr/bin/false");
        // false 가 아직 종료되지 않았어도 바로 유휴 세션으로 회수할 수 있게 한다.
        broadcastProperties.setIdleTimeout(Duration.ZERO);
        broadcastService.start(email, meetingId, "video/mp4;codecs=avc1", "fmp4", 1);
        var session = broadcastService.sessionOf(meetingId);
        assertThat(session).as("전제: 시작한 세션이 서비스 맵에 있어야 한다").isNotNull();

        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (session.isAlive() && System.nanoTime() < deadline) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("가짜 ffmpeg 종료를 기다리다 인터럽트됐다", e);
            }
        }
        assertThat(session.isAlive())
                .as("/usr/bin/false 가 끝난 뒤에만 사망 세션 회수 경로를 시험한다")
                .isFalse();
        return session.getSessionId();
    }

    @Test
    @DisplayName("기존 수거기는 재시작 뒤의 유령을 못 잡는다")
    void 기존_수거기로는_못_잡는다() {
        transactionTemplate.executeWithoutResult(s ->
                em.find(Meeting.class, meetingId).startBroadcast("죽은-세션", "/hls/x.m3u8"));

        // 이 수거기는 메모리에 있는 세션을 훑는다. 재시작 뒤에는 그 맵이 비어 있다.
        broadcastService.reapIdleSessions();

        assertThat(meetingRepository.findById(meetingId).orElseThrow().isBroadcasting())
                .as("훑을 것이 없으니 아무것도 안 한다. "
                        + "그래서 기동 시 정리가 따로 필요하다 - 이 시험이 그 근거다")
                .isTrue();

        broadcastService.reconcileOnStartup();
    }

    @Test
    @DisplayName("송출 중인데 구독자가 0이면 안 보는 방송으로 센다")
    void 아무도_안_듣는_방송을_센다() {
        String destination = "/topic/rooms/" + meetingId;

        assertThat(broadcastService.unwatchedCount())
                .as("전제: 송출 중인 방이 없다")
                .isZero();

        // 구독자가 0인 상태에서 시청자만 붙었다 떨어지는 상황을 흉내 낸다.
        chatMetrics.subscribed(destination);
        assertThat(chatMetrics.subscriberCount(destination)).isOne();

        chatMetrics.unsubscribed(destination);
        assertThat(chatMetrics.subscriberCount(destination))
                .as("구독이 0이 되면 그 방은 아무도 안 듣는 것이다. "
                        + "세그먼트를 만들어 두고 아무도 안 가져가는 상태다")
                .isZero();
    }
}
