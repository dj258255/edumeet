package com.edu.edumeet.integration.chat;

import com.edu.edumeet.chat.domain.ChatMessage;
import com.edu.edumeet.chat.dto.ChatReplayResponse;
import com.edu.edumeet.chat.repository.ChatMessageRepository;
import com.edu.edumeet.chat.service.ChatService;
import com.edu.edumeet.classroom.domain.ClassMember;
import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.domain.SessionType;
import com.edu.edumeet.member.domain.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 다시보기 채팅 조회. (#108)
 *
 * <p>#61 에서 저장은 하게 했는데 <b>읽을 길이 없었다.</b>
 * {@code recentMessages} 는 "지금 들어왔는데 방금 무슨 얘기 했나" 이고,
 * 다시보기는 <b>"재생 위치 12분 34초에 무슨 대화가 있었나"</b> 다. 다른 질문이다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("다시보기 채팅 조회")
class ChatReplayTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired ChatService chatService;
    @Autowired ChatMessageRepository chatMessageRepository;
    @Autowired TransactionTemplate transactionTemplate;
    @PersistenceContext EntityManager em;

    private record Fixture(Long meetingId, String ownerEmail, String studentEmail, String outsiderEmail) {}

    /** 회의 하나와 그 안의 채팅을 만든다. offsets 는 각 메시지의 재생 위치다. */
    private Fixture given(long... offsets) {
        int n = SEQ.incrementAndGet();
        String owner = "replay-owner-" + n + "@test";
        String student = "replay-student-" + n + "@test";
        String outsider = "replay-outsider-" + n + "@test";
        Long[] id = new Long[1];

        transactionTemplate.executeWithoutResult(s -> {
            Member o = Member.builder().email(owner).nickname("강사").password("x").build();
            Member st = Member.builder().email(student).nickname("학생").password("x").build();
            Member out = Member.builder().email(outsider).nickname("외부인").password("x").build();
            em.persist(o); em.persist(st); em.persist(out);

            ClassRoom c = ClassRoom.builder().member(o).title("클래스").description("-")
                    .participantLimit(30).isDeleted(false).build();
            em.persist(c);
            em.persist(ClassMember.builder().classRoom(c).member(st).build());

            Meeting m = Meeting.builder().classRoom(c).title("방송").description("-")
                    .sessionType(SessionType.BROADCAST)
                    .startTime(LocalDateTime.now().minusHours(2)).build();
            em.persist(m);

            for (long off : offsets) {
                em.persist(ChatMessage.of(m, "viewer@test", "at-" + off, off));
            }
            em.flush();
            id[0] = m.getId();
        });
        return new Fixture(id[0], owner, student, outsider);
    }

    @Test
    @DisplayName("★ 재생 위치로 자른다 - 구간 밖은 안 준다")
    void returns_only_the_requested_window() {
        Fixture f = given(0L, 30_000L, 59_999L, 60_000L, 90_000L);

        ChatReplayResponse r = chatService.replay(f.meetingId(), f.ownerEmail(), 0, 60_000);

        assertThat(r.messages()).hasSize(3);
        assertThat(r.messages()).extracting(ChatReplayResponse.Message::offsetMillis)
                .as("끝(to)은 제외다. 60000 이 포함되면 다음 구간과 겹쳐 두 번 보인다")
                .containsExactly(0L, 30_000L, 59_999L);
    }

    @Test
    @DisplayName("★ 재생 순서로 정렬한다 - 배치 저장이라 id 순서는 발화 순서가 아니다")
    void sorted_by_playback_position() {
        // 일부러 뒤죽박죽 저장한다. 큐에서 나온 순서지 말한 순서가 아니다.
        Fixture f = given(50_000L, 10_000L, 30_000L, 20_000L);

        ChatReplayResponse r = chatService.replay(f.meetingId(), f.ownerEmail(), 0, 60_000);

        assertThat(r.messages()).extracting(ChatReplayResponse.Message::offsetMillis)
                .as("id 순서로 주면 다시보기에서 대화가 뒤섞여 보인다")
                .containsExactly(10_000L, 20_000L, 30_000L, 50_000L);
    }

    @Test
    @DisplayName("★ offsetMillis 가 없는 옛 행은 제외한다 - 섞으면 0초에 몰려 그려진다")
    void rows_without_offset_are_excluded() {
        Fixture f = given(10_000L, 20_000L);
        // V7 이전에 저장된 것을 흉내낸다.
        transactionTemplate.executeWithoutResult(s -> {
            Meeting m = em.find(Meeting.class, f.meetingId());
            em.persist(ChatMessage.of(m, "old@test", "재생위치를 모르는 옛 메시지"));
            em.flush();
        });

        ChatReplayResponse r = chatService.replay(f.meetingId(), f.ownerEmail(), 0, 60_000);

        assertThat(r.messages()).hasSize(2);
        assertThat(r.messages()).extracting(ChatReplayResponse.Message::content)
                .doesNotContain("재생위치를 모르는 옛 메시지");

        // ★ 이 단언만으로는 IS NOT NULL 조건의 필요성을 증명하지 못한다.
        //   SQL 3값 논리에서 NULL >= 0 은 UNKNOWN 이라 범위 비교가 이미 걸러낸다.
        //   되돌려 확인했더니 조건을 빼도 시험이 통과했다.
        //
        //   그래서 응답에 담긴 offsetMillis 가 전부 실제 값인지도 본다 -
        //   누군가 COALESCE 로 null 을 0 으로 바꾸는 순간 여기서 걸린다.
        assertThat(r.messages()).allSatisfy(m ->
                assertThat(m.offsetMillis())
                        .as("재생 위치를 모르는 행이 0초로 둔갑해 들어왔다")
                        .isIn(10_000L, 20_000L));
    }

    @Test
    @DisplayName("★ 상한을 넘으면 hasMore 로 알린다 - 조용히 자르지 않는다")
    void reports_truncation() {
        long[] many = new long[600];
        for (int i = 0; i < many.length; i++) many[i] = i * 10L;
        Fixture f = given(many);

        ChatReplayResponse r = chatService.replay(f.meetingId(), f.ownerEmail(), 0, 10_000);

        assertThat(r.messages())
                .as("상한 없이 다 주면 두 시간 방송에서 응답이 수 MB 가 된다")
                .hasSize(500);
        assertThat(r.hasMore())
                .as("잘렸는데 hasMore 가 false 면 클라이언트가 '이게 전부' 로 읽는다")
                .isTrue();
    }

    @Test
    @DisplayName("잘리지 않았으면 hasMore 는 false 다")
    void no_truncation_reports_false() {
        Fixture f = given(0L, 1_000L, 2_000L);
        ChatReplayResponse r = chatService.replay(f.meetingId(), f.ownerEmail(), 0, 60_000);
        assertThat(r.hasMore()).isFalse();
    }

    @Test
    @DisplayName("★ 클래스 구성원만 본다 - 다시보기도 수업 자료다")
    void only_class_members_can_read() {
        Fixture f = given(0L, 1_000L);

        // 강사와 수강생은 된다
        assertThat(chatService.replay(f.meetingId(), f.ownerEmail(), 0, 60_000).messages()).hasSize(2);
        assertThat(chatService.replay(f.meetingId(), f.studentEmail(), 0, 60_000).messages()).hasSize(2);

        // 외부인은 안 된다
        assertThatThrownBy(() -> chatService.replay(f.meetingId(), f.outsiderEmail(), 0, 60_000))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("구간이 뒤집혀 있으면 거부한다")
    void rejects_inverted_window() {
        Fixture f = given(0L);
        assertThatThrownBy(() -> chatService.replay(f.meetingId(), f.ownerEmail(), 60_000, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> chatService.replay(f.meetingId(), f.ownerEmail(), -1, 1_000))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
