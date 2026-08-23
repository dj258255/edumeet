package com.edu.edumeet.perf;

import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.homework.domain.Assignment;
import com.edu.edumeet.homework.domain.AssignmentFileUpload;
import com.edu.edumeet.homework.domain.StudentSubmissionStatus;
import com.edu.edumeet.homework.domain.Submission;
import com.edu.edumeet.homework.domain.SubmissionFileUpload;
import com.edu.edumeet.homework.domain.SubmissionStatus;
import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.domain.SessionType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 부하 측정용 데이터를 만든다.
 *
 * <p>N+1 은 데이터가 적으면 드러나지 않는다. 과제 3개짜리 목록은 배치 페치를 꺼도
 * 빠르다. 클래스당 과제 30개, 과제당 제출 15건과 학생 상태 20건을 넣어야
 * "쿼리 수가 행 수에 비례한다"는 성질이 지연시간으로 나타난다.
 *
 * <p>규모는 application-perf.yml 의 edumeet.perf.seed 로 조절한다.
 */
@Slf4j
@Profile("perf")
@Component
public class PerfDataSeeder implements ApplicationRunner {

    @PersistenceContext
    private EntityManager em;

    @Value("${edumeet.perf.seed.classes}")           private int classes;
    @Value("${edumeet.perf.seed.assignments-per-class}")     private int assignmentsPerClass;
    @Value("${edumeet.perf.seed.students-per-assignment}")   private int studentsPerAssignment;
    @Value("${edumeet.perf.seed.submissions-per-assignment}") private int submissionsPerAssignment;
    @Value("${edumeet.perf.seed.files-per-submission}")      private int filesPerSubmission;
    @Value("${edumeet.perf.seed.files-per-assignment}")      private int filesPerAssignment;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Long existing = em.createQuery("SELECT COUNT(a) FROM Assignment a", Long.class).getSingleResult();
        if (existing > 0) {
            log.warn("시드 데이터가 이미 있다. 건너뛴다. (과제 {}건)", existing);
            return;
        }

        long start = System.currentTimeMillis();
        int rows = 0;

        for (int c = 1; c <= classes; c++) {
            for (int a = 0; a < assignmentsPerClass; a++) {
                Assignment assignment = Assignment.builder()
                        .title("과제 %d-%d 자료구조 실습".formatted(c, a))
                        .description("설명 " + "가".repeat(200))
                        .classId((long) c)
                        .createdByEmail("teacher%d@edumeet.test".formatted(c))
                        .createdByName("교사%d".formatted(c))
                        .build();
                em.persist(assignment);
                rows++;

                for (int f = 0; f < filesPerAssignment; f++) {
                    em.persist(assignmentFile(assignment, f));
                    rows++;
                }

                for (int s = 0; s < studentsPerAssignment; s++) {
                    // 앞쪽 학생은 제출, 뒤쪽은 미제출로 섞는다.
                    // 전부 같은 상태면 분기 비용이 실제와 달라진다.
                    StudentSubmissionStatus status = s < submissionsPerAssignment
                            ? StudentSubmissionStatus.submitted(assignment,
                                    studentEmail(c, s), studentName(c, s), LocalDateTime.now())
                            : StudentSubmissionStatus.notSubmitted(assignment,
                                    studentEmail(c, s), studentName(c, s));
                    em.persist(status);
                    rows++;
                }

                for (int s = 0; s < submissionsPerAssignment; s++) {
                    Submission submission = Submission.builder()
                            .assignmentId(assignment.getId())
                            .classMemberEmail(studentEmail(c, s))
                            .classMemberName(studentName(c, s))
                            .content("제출 내용 " + "나".repeat(300))
                            .status(SubmissionStatus.SUBMITTED)
                            .build();
                    em.persist(submission);
                    rows++;

                    for (int f = 0; f < filesPerSubmission; f++) {
                        em.persist(submissionFile(submission, f));
                        rows++;
                    }
                }

                // 영속성 컨텍스트가 과제 전체를 들고 있으면 시드 자체가 느려진다.
                em.flush();
                em.clear();
            }
        }

        rows += seedSession();

        log.warn("시드 완료: {}행, {}ms", rows, System.currentTimeMillis() - start);
    }

    /**
     * 정원 동시성 측정용 세션 하나를 만든다.
     *
     * <p>화상강의(INTERACTIVE)여야 정원 제한이 걸린다. 라이브방송은 정원이 없다.
     * 정원 값은 부하 회차마다 /api/perf/sessions/reset 으로 다시 설정한다.
     */
    private int seedSession() {
        ClassRoom classRoom = ClassRoom.builder()
                .title("정원 측정용 클래스")
                .description("부하 테스트 전용")
                .participantLimit(30)
                .isDeleted(false)
                .build();
        em.persist(classRoom);

        Meeting meeting = Meeting.builder()
                .classRoom(classRoom)
                .title("정원 측정용 세션")
                .description("INTERACTIVE 라 정원 제한이 적용된다")
                .sessionType(SessionType.INTERACTIVE)
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now().plusHours(6))
                .build();
        em.persist(meeting);
        em.flush();

        log.warn("정원 측정용 세션 생성: meetingId={}, classRoomId={}, 정원={}",
                meeting.getId(), classRoom.getId(), classRoom.getParticipantLimit());
        return 2;
    }

    private static String studentEmail(int c, int s) {
        return "student%d-%d@edumeet.test".formatted(c, s);
    }

    private static String studentName(int c, int s) {
        return "학생%d-%d".formatted(c, s);
    }

    private static AssignmentFileUpload assignmentFile(Assignment assignment, int ord) {
        return AssignmentFileUpload.builder()
                .assignment(assignment)
                .uuid(UUID.randomUUID().toString())
                .fileName("과제자료%d.pdf".formatted(ord))
                .ord(ord)
                .img(false)
                .fileSize(1024L * (ord + 1))
                .contentType("application/pdf")
                .uploadedBy(assignment.getCreatedByEmail())
                .referenceId(assignment.getId())
                .domain("assignment")
                .build();
    }

    private static SubmissionFileUpload submissionFile(Submission submission, int ord) {
        return SubmissionFileUpload.builder()
                .submission(submission)
                .uuid(UUID.randomUUID().toString())
                .fileName("제출물%d.zip".formatted(ord))
                .ord(ord)
                .img(false)
                .fileSize(2048L * (ord + 1))
                .contentType("application/zip")
                .uploadedBy(submission.getClassMemberEmail())
                .referenceId(submission.getId())
                .domain("submission")
                .build();
    }
}
