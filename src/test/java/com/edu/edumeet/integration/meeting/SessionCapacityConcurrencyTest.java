package com.edu.edumeet.integration.meeting;

import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.classroom.repository.ClassRepository;
import com.edu.edumeet.member.domain.Member;
import com.edu.edumeet.member.repository.MemberRepository;
import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.domain.SessionType;
import com.edu.edumeet.meeting.repository.MeetingParticipantRepository;
import com.edu.edumeet.meeting.repository.MeetingRepository;
import com.edu.edumeet.meeting.service.MeetingService;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 세션 정원의 동시성 검증.
 *
 * 정원 검증은 "현재 인원을 세고 -> 정원과 비교하고 -> 참가를 기록한다"의 세 단계다.
 * 이 구간이 원자적이지 않으면 동시 요청이 모두 검사를 통과해 정원을 넘긴다.
 *
 * 라이브 시작 직후처럼 입장이 한 순간에 몰리는 상황이 정확히 이 경우다.
 *
 * 트랜잭션 경계를 스레드마다 따로 가져가야 하므로 이 테스트에는 @Transactional 을 쓰지 않는다.
 * 클래스 레벨 트랜잭션을 걸면 모든 스레드가 같은 커넥션을 공유해 경쟁이 재현되지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Log4j2
class SessionCapacityConcurrencyTest {

    private static final int CAPACITY = 3;
    private static final int CONCURRENT_REQUESTS = 20;

    @Autowired private MeetingService meetingService;
    @Autowired private MeetingRepository meetingRepository;
    @Autowired private MeetingParticipantRepository participantRepository;
    @Autowired private ClassRepository classRepository;
    @Autowired private MemberRepository memberRepository;

    private Long interactiveMeetingId;
    private Long broadcastMeetingId;

    @BeforeEach
    void setUp() {
        Member owner = memberRepository.save(Member.builder()
                .email("owner-" + System.nanoTime() + "@example.com")
                .password("password")
                .nickname("강사")
                .build());

        ClassRoom classRoom = classRepository.save(ClassRoom.builder()
                .member(owner)
                .title("동시성 테스트 강의실")
                .description("정원 " + CAPACITY + "명")
                .participantLimit(CAPACITY)
                .build());

        interactiveMeetingId = meetingRepository.save(Meeting.builder()
                .classRoom(classRoom)
                .title("화상강의 회차")
                .sessionType(SessionType.INTERACTIVE)
                .startTime(LocalDateTime.now())
                .build()).getId();

        broadcastMeetingId = meetingRepository.save(Meeting.builder()
                .classRoom(classRoom)
                .title("라이브방송 회차")
                .sessionType(SessionType.BROADCAST)
                .startTime(LocalDateTime.now())
                .build()).getId();
    }

    @AfterEach
    void tearDown() {
        participantRepository.deleteAll();
        meetingRepository.deleteAll();
        classRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("화상강의는 동시 입장이 몰려도 정원을 넘지 않는다")
    void interactiveSessionMustNotExceedCapacity() throws InterruptedException {
        Result result = joinConcurrently(interactiveMeetingId);

        log.warn("[측정] 화상강의 정원 {}명 · 동시 요청 {}건 -> 성공 {} / 거절 {}",
                CAPACITY, CONCURRENT_REQUESTS, result.success.get(), result.rejected.get());

        long joined = participantRepository.countActiveByMeetingId(interactiveMeetingId);
        log.warn("[측정] 실제 입장 인원 {}명", joined);

        assertThat(result.success.get())
                .as("정원 %d명에 동시 요청 %d건 -> 성공은 정확히 %d건이어야 한다",
                        CAPACITY, CONCURRENT_REQUESTS, CAPACITY)
                .isEqualTo(CAPACITY);

        assertThat(joined)
                .as("DB 에 기록된 입장 인원이 정원을 넘으면 안 된다")
                .isEqualTo(CAPACITY);
    }

    @Test
    @DisplayName("라이브방송은 정원 제한이 없어 동시 입장이 모두 성공한다")
    void broadcastSessionHasNoCapacityLimit() throws InterruptedException {
        Result result = joinConcurrently(broadcastMeetingId);

        log.warn("[측정] 라이브방송 · 동시 요청 {}건 -> 성공 {} / 거절 {}",
                CONCURRENT_REQUESTS, result.success.get(), result.rejected.get());

        assertThat(result.success.get())
                .as("라이브방송은 정원 제한이 없다")
                .isEqualTo(CONCURRENT_REQUESTS);
        assertThat(result.rejected.get()).isZero();
    }

    private Result joinConcurrently(Long meetingId) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(CONCURRENT_REQUESTS);
        Result result = new Result();

        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            final String email = "student" + i + "@example.com";
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();                 // 모든 스레드를 같은 순간에 출발시킨다
                    meetingService.joinSession(meetingId, email, false);
                    result.success.incrementAndGet();
                } catch (Exception e) {
                    result.rejected.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(10, TimeUnit.SECONDS);
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        pool.shutdown();
        return result;
    }

    private static class Result {
        final AtomicInteger success = new AtomicInteger();
        final AtomicInteger rejected = new AtomicInteger();
    }
}
