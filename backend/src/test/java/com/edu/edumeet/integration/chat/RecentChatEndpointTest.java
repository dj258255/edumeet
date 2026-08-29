package com.edu.edumeet.integration.chat;

import com.edu.edumeet.chat.dto.ChatMessageResponse;
import com.edu.edumeet.chat.service.ChatService;
import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.domain.MeetingParticipant;
import com.edu.edumeet.meeting.domain.SessionType;
import com.edu.edumeet.member.domain.Member;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 입장 시 지난 대화를 실제로 꺼낼 수 있는지 고정한다. (#170)
 *
 * <h3>왜 이 시험이 생겼나</h3>
 * {@code ChatService.recentMessages} 는 오래 전부터 있었는데 <b>아무도 안 불렀다.</b>
 * 컨트롤러도 시험도 프론트도 안 부르고 주석에서만 언급됐다.
 *
 * <p>결과는 <i>"채팅이 안 나온다"</i> 가 아니라 <b>"새로고침하면 화면이 빈다"</b> 였다.
 * 에러가 아니라 빈 화면이라 아무도 고장이라고 느끼지 않는다.
 * 이 저장소가 반복해 잡은 모양이다 — 만들어 놓고 연결하지 않은 것.
 *
 * <p>그래서 <b>메서드가 도는지가 아니라 HTTP 로 닿는지</b>를 본다.
 * 서비스만 시험하면 이번과 똑같이 "되는데 아무도 못 쓰는" 상태가 다시 만들어진다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("입장 시 지난 대화")
class RecentChatEndpointTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired ChatService chatService;
    @Autowired com.edu.edumeet.chat.service.ChatArchiveQueue archiveQueue;
    @Autowired RequestMappingHandlerMapping handlerMapping;
    @Autowired TransactionTemplate transactionTemplate;
    @PersistenceContext EntityManager em;

    private Long meetingId;
    private Long classRoomId;
    private String member;
    private String outsider;

    @BeforeEach
    void setUp() {
        int n = SEQ.incrementAndGet();
        member = "recent-chat-" + n + "@test";
        outsider = "recent-outsider-" + n + "@test";
        transactionTemplate.executeWithoutResult(s -> {
            Member owner = Member.builder().email(member).nickname("참가자").password("x").build();
            em.persist(owner);
            em.persist(Member.builder().email(outsider).nickname("외부인").password("x").build());
            ClassRoom room = ClassRoom.builder().member(owner).title("클래스").description("-")
                    .participantLimit(30).isDeleted(false).build();
            em.persist(room);
            Meeting meeting = Meeting.builder().classRoom(room).title("방송").description("-")
                    .sessionType(SessionType.BROADCAST)
                    .startTime(LocalDateTime.now().minusMinutes(10)).build();
            em.persist(meeting);
            em.persist(MeetingParticipant.join(meeting, member));
            em.flush();
            meetingId = meeting.getId();
            classRoomId = room.getId();
        });
    }

    @AfterEach
    void tearDown() {
        // 만든 것까지만 지운다. 전체를 지우면 같은 컨텍스트의 다른 시험이 깨진다.
        transactionTemplate.executeWithoutResult(s -> {
            em.createQuery("DELETE FROM ChatMessage c WHERE c.meeting.id = :id")
                    .setParameter("id", meetingId).executeUpdate();
            em.createQuery("DELETE FROM MeetingParticipant p WHERE p.meeting.id = :id")
                    .setParameter("id", meetingId).executeUpdate();
            em.createQuery("DELETE FROM Meeting m WHERE m.id = :id")
                    .setParameter("id", meetingId).executeUpdate();
            em.createQuery("DELETE FROM ClassRoom c WHERE c.id = :id")
                    .setParameter("id", classRoomId).executeUpdate();
            em.createQuery("DELETE FROM Member m WHERE m.email IN (:a, :b)")
                    .setParameter("a", member).setParameter("b", outsider).executeUpdate();
        });
    }

    @Test
    @DisplayName("★ HTTP 로 닿는 경로가 있다")
    void 엔드포인트가_실재한다() {
        boolean exists = handlerMapping.getHandlerMethods().entrySet().stream()
                .anyMatch(e -> patternsOf(e.getKey()).contains(
                        "/api/v1/meeting/{meetingId}/chat/recent"));

        assertThat(exists)
                .as("서비스 메서드만 있고 부르는 곳이 없으면 '되는데 아무도 못 쓰는' 상태다. "
                        + "실제로 그랬고, 증상은 에러가 아니라 빈 화면이라 아무도 고장이라고 느끼지 않았다")
                .isTrue();
    }

    @Test
    @DisplayName("방송 채팅도 나온다 - 다만 배치가 넣은 뒤부터다")
    void 방송도_지난_대화가_나온다() {
        chatService.handle(meetingId, member, "먼저 한 말");
        chatService.handle(meetingId, member, "그다음 말");

        // ★ 바로는 안 보인다.
        //   방송 채팅은 발행 경로에서 저장하지 않는다. 큐에 넣고 배치가 가져간다(#61).
        //   발행 경로의 DB 쓰기가 브로드캐스트 측정을 가리기 때문이다.
        //
        //   그래서 "방금 한 말" 이 지난 대화에 뜨기까지 flush 주기(기본 1초)만큼 늦는다.
        //   이건 결함이 아니라 그 설계의 대가다. 시험이 그 사실을 그대로 담는다.
        assertThat(chatService.recentMessages(meetingId, member))
                .as("발행 직후에는 아직 큐에 있다. 이 지연이 비동기 저장의 대가다")
                .isEmpty();

        archiveQueue.flush();

        assertThat(chatService.recentMessages(meetingId, member))
                .extracting(ChatMessageResponse::content)
                .as("예전 주석은 'BROADCAST 는 항상 빈 목록' 이라고 했는데 #61 이후로 틀린 말이다")
                .containsExactly("먼저 한 말", "그다음 말");
    }

    @Test
    @DisplayName("그 방을 볼 수 없는 사람은 지난 대화도 못 본다")
    void 외부인은_못_본다() {
        chatService.handle(meetingId, member, "우리끼리 한 말");

        assertThatThrownBy(() -> chatService.recentMessages(meetingId, outsider))
                .as("실시간으로 못 보는 것을 조회로 우회할 수 있으면 구독 인가가 의미가 없다")
                .isInstanceOf(RuntimeException.class);
    }

    @SuppressWarnings("unchecked")
    private List<String> patternsOf(RequestMappingInfo info) {
        return info.getPathPatternsCondition() != null
                ? info.getPathPatternsCondition().getPatternValues().stream().toList()
                : List.of();
    }
}
