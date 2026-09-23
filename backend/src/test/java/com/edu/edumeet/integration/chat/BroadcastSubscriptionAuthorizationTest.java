package com.edu.edumeet.integration.chat;

import com.edu.edumeet.chat.config.StompAuthChannelInterceptor;
import com.edu.edumeet.classroom.domain.ClassMember;
import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.member.domain.Member;
import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.domain.SessionType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

/**
 * 방송 시청자는 회의 참가자가 아니라 수업 소속으로 인가한다. (#227)
 *
 * <p>인터셉터 빈을 직접 받아 검증하므로 생성자 모양과 무관하다. 이전 구현은 이 수업 멤버의
 * 참가 기록을 찾다가 거절하므로, 이 시험은 이전 코드에서도 컴파일되고 행동으로 실패한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("방송 STOMP 구독 인가")
class BroadcastSubscriptionAuthorizationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired StompAuthChannelInterceptor interceptor;
    @Autowired TransactionTemplate transactionTemplate;
    @PersistenceContext EntityManager em;

    private Long meetingId;
    private Long classId;
    private String ownerEmail;
    private String memberEmail;

    @BeforeEach
    void setUp() {
        int n = SEQ.incrementAndGet();
        ownerEmail = "broadcast-owner-" + n + "@test";
        memberEmail = "broadcast-member-" + n + "@test";

        transactionTemplate.executeWithoutResult(status -> {
            Member owner = Member.builder().email(ownerEmail).nickname("수업 주인").password("x").build();
            Member member = Member.builder().email(memberEmail).nickname("수업 멤버").password("x").build();
            em.persist(owner);
            em.persist(member);

            ClassRoom classRoom = ClassRoom.builder()
                    .member(owner).title("방송 수업").description("-")
                    .participantLimit(30).isDeleted(false).build();
            em.persist(classRoom);
            em.persist(ClassMember.builder().classRoom(classRoom).member(member).build());

            Meeting meeting = Meeting.builder()
                    .classRoom(classRoom).title("방송").description("-")
                    .sessionType(SessionType.BROADCAST).startTime(LocalDateTime.now()).build();
            em.persist(meeting);
            em.flush();

            meetingId = meeting.getId();
            classId = classRoom.getId();
            assertThat(em.createQuery("SELECT COUNT(p) FROM MeetingParticipant p WHERE p.meeting.id = :id", Long.class)
                    .setParameter("id", meetingId)
                    .getSingleResult()).isZero();
        });
    }

    @AfterEach
    void tearDown() {
        if (meetingId == null) return;
        Long id = meetingId;
        Long roomId = classId;
        List<String> emails = List.of(ownerEmail, memberEmail);
        transactionTemplate.executeWithoutResult(status -> {
            em.createQuery("DELETE FROM MeetingParticipant p WHERE p.meeting.id = :id")
                    .setParameter("id", id).executeUpdate();
            em.createQuery("DELETE FROM Meeting m WHERE m.id = :id")
                    .setParameter("id", id).executeUpdate();
            em.createQuery("DELETE FROM ClassMember cm WHERE cm.classRoom.id = :classId")
                    .setParameter("classId", roomId).executeUpdate();
            em.createQuery("DELETE FROM ClassRoom c WHERE c.id = :classId")
                    .setParameter("classId", roomId).executeUpdate();
            em.createQuery("DELETE FROM Member m WHERE m.email IN :emails")
                    .setParameter("emails", emails).executeUpdate();
        });
    }

    @Test
    @DisplayName("★ 방송 수업 멤버는 참가 기록 없이 SUBSCRIBE 할 수 있다")
    void broadcast_class_member_without_participant_record_can_subscribe() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/rooms/" + meetingId);
        accessor.setUser(new UsernamePasswordAuthenticationToken(memberEmail, null));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatCode(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .doesNotThrowAnyException();
    }
}
