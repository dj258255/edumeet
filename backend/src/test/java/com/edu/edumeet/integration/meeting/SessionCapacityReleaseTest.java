package com.edu.edumeet.integration.meeting;

import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.member.domain.Member;
import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.domain.MeetingParticipant;
import com.edu.edumeet.meeting.domain.SessionType;
import com.edu.edumeet.meeting.repository.MeetingParticipantRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 정원이 실제로 반환되는지 검증한다.
 *
 * <p>#12 에서 비관적 잠금으로 정원 초과 입장을 막았지만 <b>반환 경로가 없어서</b>
 * 방이 한 번 차면 영영 꽉 차 있었다. 잠금 테스트는 통과하는데 기능은 반쪽이었다. (#23)
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SessionCapacityReleaseTest {

    @Autowired
    private MeetingParticipantRepository participantRepository;

    @PersistenceContext
    private EntityManager em;

    private Meeting givenMeetingWithParticipants(int count) {
        Member owner = Member.builder()
                .email("owner@test").nickname("주인").password("x").build();
        em.persist(owner);

        ClassRoom classRoom = ClassRoom.builder()
                .member(owner).title("테스트 클래스").description("-")
                .participantLimit(3).isDeleted(false).build();
        em.persist(classRoom);

        Meeting meeting = Meeting.builder()
                .classRoom(classRoom).title("세션").description("-")
                .sessionType(SessionType.INTERACTIVE)
                .startTime(LocalDateTime.now()).endTime(LocalDateTime.now().plusHours(1))
                .build();
        em.persist(meeting);

        for (int i = 0; i < count; i++) {
            em.persist(MeetingParticipant.join(meeting, "user" + i + "@test"));
        }
        em.flush();
        return meeting;
    }

    @Test
    @DisplayName("참가자가 나가면 정원이 반환된다")
    void 참가자가_나가면_정원이_반환된다() {
        Meeting meeting = givenMeetingWithParticipants(3);
        assertThat(participantRepository.countActiveByMeetingId(meeting.getId())).isEqualTo(3);

        participantRepository.findActive(meeting.getId(), "user0@test")
                .ifPresent(MeetingParticipant::leave);
        em.flush();

        assertThat(participantRepository.countActiveByMeetingId(meeting.getId()))
                .as("한 명이 나갔으므로 정원이 하나 반환되어야 한다")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("회의를 종료하면 남은 참가 기록이 전부 닫힌다")
    void 회의를_종료하면_참가기록이_전부_닫힌다() {
        Meeting meeting = givenMeetingWithParticipants(3);

        int closed = participantRepository.closeAllActive(meeting.getId(), LocalDateTime.now());

        assertThat(closed).isEqualTo(3);
        assertThat(participantRepository.countActiveByMeetingId(meeting.getId()))
                .as("회의 종료 후에는 활성 참가자가 없어야 한다")
                .isZero();
    }

    @Test
    @DisplayName("이미 나간 참가자는 다시 닫히지 않는다 — closeAllActive 는 멱등이다")
    void closeAllActive_는_멱등이다() {
        Meeting meeting = givenMeetingWithParticipants(2);

        int first = participantRepository.closeAllActive(meeting.getId(), LocalDateTime.now());
        int second = participantRepository.closeAllActive(meeting.getId(), LocalDateTime.now());

        assertThat(first).isEqualTo(2);
        assertThat(second)
                .as("두 번째 호출은 닫을 것이 없어야 한다")
                .isZero();
    }
}
