package com.edu.edumeet.integration.chat;

import com.edu.edumeet.chat.repository.ChatMessageRepository;
import com.edu.edumeet.chat.service.ChatArchiveQueue;
import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.domain.SessionType;
import com.edu.edumeet.meeting.repository.MeetingRepository;
import com.edu.edumeet.member.domain.Member;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

/**
 * 다시보기 채팅 저장 배치가 바깥 트랜잭션 안에서 조회하는지 고정한다.
 */
@SpringBootTest(properties = "edumeet.chat.archive.flush-interval-ms=600000")
@ActiveProfiles("test")
@DisplayName("다시보기 채팅 트랜잭션")
class ChatArchiveTransactionTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired ChatArchiveQueue archiveQueue;
    @Autowired ChatMessageRepository chatMessageRepository;
    @Autowired TransactionTemplate transactionTemplate;
    @PersistenceContext EntityManager em;
    @MockitoSpyBean MeetingRepository meetingRepository;

    private final List<Long> meetingIds = new ArrayList<>();
    private final List<Long> classRoomIds = new ArrayList<>();
    private final List<String> memberEmails = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (int i = 0; i < 100 && archiveQueue.queuedCount() > 0; i++) {
            archiveQueue.flush();
        }
        transactionTemplate.executeWithoutResult(status -> {
            for (Long meetingId : meetingIds) {
                em.createQuery("DELETE FROM ChatMessage c WHERE c.meeting.id = :id")
                        .setParameter("id", meetingId)
                        .executeUpdate();
                em.createQuery("DELETE FROM Meeting m WHERE m.id = :id")
                        .setParameter("id", meetingId)
                        .executeUpdate();
            }
            for (Long classRoomId : classRoomIds) {
                em.createQuery("DELETE FROM ClassRoom c WHERE c.id = :id")
                        .setParameter("id", classRoomId)
                        .executeUpdate();
            }
            for (String email : memberEmails) {
                em.createQuery("DELETE FROM Member m WHERE m.email = :email")
                        .setParameter("email", email)
                        .executeUpdate();
            }
        });
        meetingIds.clear();
        classRoomIds.clear();
        memberEmails.clear();
    }

    @Test
    @DisplayName("★ 저장 배치는 바깥 트랜잭션 안에서 조회한다")
    void findById_runs_inside_outer_transaction() {
        Long meetingId = givenMeeting();
        List<Boolean> transactionActiveValues = new ArrayList<>();

        doAnswer(invocation -> {
            transactionActiveValues.add(TransactionSynchronizationManager.isActualTransactionActive());
            return invocation.callRealMethod();
        }).when(meetingRepository).findById(ArgumentMatchers.anyLong());

        archiveQueue.offer(meetingId, "viewer@test", "트랜잭션 확인", 0L);
        archiveQueue.flush();

        assertThat(transactionActiveValues)
                .as("저장 배치를 감싸는 바깥 트랜잭션이 없다")
                .containsExactly(true);
    }

    private Long givenMeeting() {
        int n = SEQ.incrementAndGet();
        Long[] ids = new Long[2];
        String email = "archive-transaction-" + n + "@test";
        transactionTemplate.executeWithoutResult(status -> {
            Member owner = Member.builder()
                    .email(email).nickname("호스트").password("x").build();
            em.persist(owner);
            ClassRoom classRoom = ClassRoom.builder().member(owner)
                    .title("클래스").description("-").participantLimit(30).isDeleted(false).build();
            em.persist(classRoom);
            Meeting meeting = Meeting.builder().classRoom(classRoom)
                    .title("세션").description("-").sessionType(SessionType.BROADCAST)
                    .startTime(LocalDateTime.now().minusMinutes(1)).build();
            em.persist(meeting);
            em.flush();
            ids[0] = meeting.getId();
            ids[1] = classRoom.getId();
        });
        meetingIds.add(ids[0]);
        classRoomIds.add(ids[1]);
        memberEmails.add(email);
        return ids[0];
    }
}
