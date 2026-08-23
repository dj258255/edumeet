package com.edu.edumeet.integration.meeting;

import com.edu.edumeet.classroom.domain.ClassMember;
import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.classroom.repository.ClassRepository;
import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.domain.SessionType;
import com.edu.edumeet.meeting.dto.request.MeetingCreateRequestDto;
import com.edu.edumeet.meeting.repository.MeetingRepository;
import com.edu.edumeet.meeting.service.MeetingService;
import com.edu.edumeet.member.domain.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 방송 모드 세 개를 실제로 만들 수 있는가. (#76)
 *
 * <p><b>만들 수 없었다.</b> {@code MeetingCreateRequestDto} 에 {@code sessionType} 이 없고
 * {@code Meeting.builder()} 도 그것을 넣지 않아서,
 * <b>API 로 만든 모든 세션이 필드 기본값인 {@code INTERACTIVE} 였다.</b>
 *
 * <p>그래서 아래가 전부 <b>도달할 수 없는 코드</b>였다.
 * <pre>
 *   #43  세션 타입별 정원·발행 정책
 *   #65  오디오 방송 + 자막
 *   #72  오디오 전용 토큰 강제
 *   #75  HLS 송출          ← INTERACTIVE 를 거부하므로 절대 실행될 수 없었다
 * </pre>
 *
 * <p>이 저장소에서 <b>"선언은 있는데 아무도 안 쓴다" 의 네 번째</b>이고 제일 크다.
 * 앞의 셋({@code probes.enabled} · {@code prometheus.export.enabled} · {@code isAudioOnly})은
 * 기능 하나가 죽어 있었지만, 이건 <b>기능 네 개가 통째로 죽어 있었다.</b>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("방송 모드 세 개를 만들 수 있다")
class SessionTypeCreationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired MeetingService meetingService;
    @Autowired MeetingRepository meetingRepository;
    @Autowired ClassRepository classRepository;
    @Autowired TransactionTemplate transactionTemplate;
    @PersistenceContext EntityManager em;

    private record Fixture(Long ownerId, Long studentId, Long classId) {}

    private Fixture given() {
        int n = SEQ.incrementAndGet();
        Long[] ids = new Long[3];
        transactionTemplate.executeWithoutResult(status -> {
            Member owner = Member.builder()
                    .email("owner-" + n + "@test").nickname("강사").password("x").build();
            Member student = Member.builder()
                    .email("student-" + n + "@test").nickname("학생").password("x").build();
            em.persist(owner);
            em.persist(student);
            ClassRoom classRoom = ClassRoom.builder()
                    .member(owner).title("클래스").description("-")
                    .participantLimit(30).isDeleted(false).build();
            em.persist(classRoom);
            em.persist(ClassMember.builder().classRoom(classRoom).member(student).build());
            em.flush();
            ids[0] = owner.getId();
            ids[1] = student.getId();
            ids[2] = classRoom.getId();
        });
        return new Fixture(ids[0], ids[1], ids[2]);
    }

    private MeetingCreateRequestDto request(Long classId, SessionType type) {
        return MeetingCreateRequestDto.builder()
                .title("세션 " + SEQ.incrementAndGet())
                .description("-")
                .classId(classId)
                .sessionType(type)
                .build();
    }

    @ParameterizedTest
    @EnumSource(SessionType.class)
    @DisplayName("★ 세 모드 전부 요청한 대로 만들어진다")
    void every_mode_is_creatable(SessionType type) {
        Fixture fixture = given();

        Long meetingId = meetingService.create(fixture.ownerId(), request(fixture.classId(), type))
                .getMeetingId();

        Meeting saved = meetingRepository.findById(meetingId).orElseThrow();
        assertThat(saved.getSessionType())
                .as("요청한 모드가 아니라 %s 로 저장되면, 그 모드의 정책이 전부 죽는다", saved.getSessionType())
                .isEqualTo(type);
    }

    @Test
    @DisplayName("모드를 안 주면 화상강의다 - 기존 클라이언트가 그대로 돈다")
    void missing_type_defaults_to_interactive() {
        Fixture fixture = given();

        Long meetingId = meetingService.create(fixture.ownerId(),
                request(fixture.classId(), null)).getMeetingId();

        assertThat(meetingRepository.findById(meetingId).orElseThrow().getSessionType())
                .isEqualTo(SessionType.INTERACTIVE);
    }

    @Test
    @DisplayName("★ 참가자는 방송을 열 수 없다 - egress 코어를 먹고 클래스를 대표한다")
    void participant_cannot_open_a_broadcast() {
        Fixture fixture = given();

        assertThatThrownBy(() -> meetingService.create(
                fixture.studentId(), request(fixture.classId(), SessionType.BROADCAST)))
                .isInstanceOf(AccessDeniedException.class);

        assertThatThrownBy(() -> meetingService.create(
                fixture.studentId(), request(fixture.classId(), SessionType.AUDIO_BROADCAST)))
                .isInstanceOf(AccessDeniedException.class);
    }

    /**
     * 이 테스트가 없으면 아래 버그가 <b>세션 타입 테스트에 얹혀서 우연히</b>만 잡힌다.
     * 원인이 다르므로 따로 이름을 붙여 고정한다.
     */
    @Test
    @DisplayName("★ 세션 생성은 한 트랜잭션이다 - 응답에 소유자 이메일이 담긴다")
    void creation_runs_in_one_transaction() {
        Fixture fixture = given();
        long before = meetingRepository.count();

        var response = meetingService.create(fixture.ownerId(),
                request(fixture.classId(), SessionType.BROADCAST));

        assertThat(response.getEmail())
                .as("""
                    @Transactional 이 없으면 여기서 LazyInitializationException 이 난다.
                    open-in-view=false 라 ClassRoom 이 준영속으로 돌아오고,
                    권한 검사의 getMember().getId() 는 프록시가 DB 없이 답해서 통과하지만
                    email 은 초기화가 필요하다.""")
                .isNotBlank()
                .startsWith("owner-").endsWith("@test");

        assertThat(meetingRepository.count())
                .as("""
                    save() 가 자기 트랜잭션으로 먼저 커밋되면
                    응답이 터져도 세션은 DB 에 남는다 - 아무도 못 쓰는 세션이 쌓인다.""")
                .isEqualTo(before + 1);
    }

    @Test
    @DisplayName("참가자도 화상강의는 연다 - 스터디 모임을 막을 이유가 없다")
    void participant_can_still_open_an_interactive_session() {
        Fixture fixture = given();

        Long meetingId = meetingService.create(
                fixture.studentId(), request(fixture.classId(), SessionType.INTERACTIVE)).getMeetingId();

        assertThat(meetingRepository.findById(meetingId).orElseThrow().getSessionType())
                .isEqualTo(SessionType.INTERACTIVE);
    }
}
