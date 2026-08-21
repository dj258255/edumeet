package com.edu.edumeet.integration.homework;

import com.edu.edumeet.homework.application.AssignmentServiceImpl;
import com.edu.edumeet.homework.domain.SubmissionStatus;
import com.edu.edumeet.homework.infrastructure.AssignmentFileUploadJpaEntity;
import com.edu.edumeet.homework.infrastructure.AssignmentJpaEntity;
import com.edu.edumeet.homework.infrastructure.AssignmentJpaRepository;
import com.edu.edumeet.homework.infrastructure.StudentSubmissionStatusJpaEntity;
import com.edu.edumeet.homework.presentation.dto.AssignmentDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.log4j.Log4j2;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 과제 목록 조회의 쿼리 수를 단언하는 테스트.
 *
 * 기존 N1QueryOptimizationTest 는 System.currentTimeMillis() 로 시간만 재고
 * 결과 개수만 단언한다. 페치 조인을 전부 제거해도 통과한다.
 *
 * 이 테스트는 Hibernate Statistics 로 실제 실행된 쿼리 수를 세어
 * N+1 이 존재하는지를 환경과 무관하게 검증한다.
 *
 * 측정 지표로 시간을 쓰지 않는 이유는 CONVENTIONS.md 0.5 참조.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Rollback
@Log4j2
class AssignmentListQueryCountTest {

    /** 비교 기준이 되는 적은 과제 수 */
    private static final int SMALL_COUNT = 5;
    /** 비교 대상이 되는 많은 과제 수 (SMALL_COUNT 의 4배) */
    private static final int LARGE_COUNT = 20;
    /**
     * 허용 오차. 컬렉션 배치 로딩(@BatchSize)은 건수가 늘면
     * 배치 묶음이 한두 번 더 나갈 수 있으므로 약간의 여유를 둔다.
     * 건수에 '비례'하는 증가는 이 오차로 흡수되지 않는다.
     */
    private static final int QUERY_COUNT_TOLERANCE = 3;
    /** 과제당 첨부파일 수 */
    private static final int ATTACHMENTS_PER_ASSIGNMENT = 2;
    /** 과제당 제출현황 수 */
    private static final int STATUSES_PER_ASSIGNMENT = 3;

    @Autowired
    private AssignmentServiceImpl assignmentService;

    @Autowired
    private AssignmentJpaRepository assignmentJpaRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private EntityManager entityManager;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
    }

    /** 영속성 컨텍스트와 통계를 비우고 대상 메서드를 실행해 쿼리 수를 센다. */
    private long countQueries(Runnable action) {
        entityManager.flush();
        entityManager.clear();
        statistics.clear();
        action.run();
        return statistics.getPrepareStatementCount();
    }

    @Test
    @DisplayName("과제 목록 조회의 쿼리 수는 과제 건수에 비례하지 않아야 한다")
    void queryCountMustNotScaleWithAssignmentCount() {
        createAssignments(SMALL_COUNT, 8001L);
        long small = countQueries(() -> assignmentService.getAssignmentsByClassId(8001L));

        createAssignments(LARGE_COUNT, 8002L);
        long large = countQueries(() -> assignmentService.getAssignmentsByClassId(8002L));

        log.warn("[측정] 과제 {}건 -> 쿼리 {}건", SMALL_COUNT, small);
        log.warn("[측정] 과제 {}건 -> 쿼리 {}건", LARGE_COUNT, large);
        log.warn("[측정] 과제가 {}배 늘 때 쿼리는 {}배 늘었다",
                LARGE_COUNT / SMALL_COUNT, large / (double) small);

        // 과제 건수가 4배가 되어도 쿼리 수는 거의 그대로여야 한다.
        // 비례해서 늘어난다면 목록 조회 안에서 건별 쿼리가 나가고 있다는 뜻이다.
        assertThat(large)
                .as("과제 %d건(쿼리 %d) -> %d건(쿼리 %d). 쿼리 수가 건수에 비례한다면 N+1이다",
                        SMALL_COUNT, small, LARGE_COUNT, large)
                .isLessThanOrEqualTo(small + QUERY_COUNT_TOLERANCE);
    }

    @Test
    @DisplayName("사용자 제출상태 포함 목록 조회의 쿼리 수도 과제 건수에 비례하지 않아야 한다")
    void queryCountWithUserStatusMustNotScaleWithAssignmentCount() {
        createAssignments(SMALL_COUNT, 8003L);
        long small = countQueries(() ->
                assignmentService.getAssignmentsByClassIdWithUserStatus(8003L, "1@example.com"));

        createAssignments(LARGE_COUNT, 8004L);
        long large = countQueries(() ->
                assignmentService.getAssignmentsByClassIdWithUserStatus(8004L, "1@example.com"));

        log.warn("[측정] 사용자상태 포함 - 과제 {}건 -> 쿼리 {}건", SMALL_COUNT, small);
        log.warn("[측정] 사용자상태 포함 - 과제 {}건 -> 쿼리 {}건", LARGE_COUNT, large);

        assertThat(large)
                .as("과제 %d건(쿼리 %d) -> %d건(쿼리 %d)",
                        SMALL_COUNT, small, LARGE_COUNT, large)
                .isLessThanOrEqualTo(small + QUERY_COUNT_TOLERANCE);
    }

    private void createAssignments(int count, Long classId) {
        for (int a = 1; a <= count; a++) {
            AssignmentJpaEntity assignment = AssignmentJpaEntity.builder()
                    .title("과제 " + a)
                    .description("쿼리 수 측정용 과제")
                    .classId(classId)
                    .createdByEmail("teacher@example.com")
                    .createdByName("선생님")
                    .attachmentFiles(new HashSet<>())
                    .studentSubmissionStatuses(new HashSet<>())
                    .build();

            assignment = assignmentJpaRepository.save(assignment);

            for (int f = 1; f <= ATTACHMENTS_PER_ASSIGNMENT; f++) {
                assignment.getAttachmentFiles().add(
                        AssignmentFileUploadJpaEntity.builder()
                                .assignment(assignment)
                                .uuid("uuid-" + classId + "-" + a + "-" + f)
                                .fileName("file" + a + "-" + f + ".pdf")
                                .ord(f)
                                .img(false)
                                .fileSize(1024L)
                                .contentType("application/pdf")
                                .uploadedBy("선생님")
                                .referenceId(1L)
                                .build());
            }

            for (int s = 1; s <= STATUSES_PER_ASSIGNMENT; s++) {
                assignment.getStudentSubmissionStatuses().add(
                        StudentSubmissionStatusJpaEntity.builder()
                                .assignment(assignment)
                                .studentEmail(s + "@example.com")
                                .studentName("학생" + s)
                                .status(s % 2 == 0 ? SubmissionStatus.SUBMITTED : SubmissionStatus.NOT_SUBMITTED)
                                .submittedAt(s % 2 == 0 ? LocalDateTime.now() : null)
                                .build());
            }

            assignmentJpaRepository.save(assignment);
        }
    }
}
