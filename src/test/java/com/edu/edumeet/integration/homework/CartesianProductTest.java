package com.edu.edumeet.integration.homework;

import com.edu.edumeet.homework.service.AssignmentService;
import com.edu.edumeet.homework.domain.SubmissionStatus;
import com.edu.edumeet.homework.dto.AssignmentDTO;
import com.edu.edumeet.homework.domain.AssignmentFileUpload;
import com.edu.edumeet.homework.domain.Assignment;
import com.edu.edumeet.homework.repository.AssignmentRepository;
import com.edu.edumeet.homework.domain.StudentSubmissionStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import lombok.extern.log4j.Log4j2;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 컬렉션 두 개를 동시에 LEFT JOIN FETCH 할 때 발생하는 카테시안 곱을 측정한다.
 *
 * Set 으로 매핑되어 있어 MultipleBagFetchException 은 발생하지 않는다.
 * 그래서 문제가 드러나지 않았다.
 * 그러나 DB 는 첨부 M건 x 제출현황 N건 = M x N 행을 읽어 보내고,
 * 애플리케이션이 그것을 M + N 개로 줄인다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Rollback
@Log4j2
class CartesianProductTest {

    private static final int ATTACHMENTS = 5;
    private static final int STATUSES = 20;
    private static final Long CLASS_ID = 7001L;

    @Autowired
    private AssignmentRepository assignmentJpaRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private AssignmentService assignmentService;

    private Long assignmentId;

    @BeforeEach
    void setUp() {
        Assignment assignment = Assignment.builder()
                .title("카테시안 곱 측정용 과제")
                .description("첨부 " + ATTACHMENTS + "건, 제출현황 " + STATUSES + "건")
                .classId(CLASS_ID)
                .createdByEmail("teacher@example.com")
                .createdByName("선생님")
                .attachmentFiles(new HashSet<>())
                .studentSubmissionStatuses(new HashSet<>())
                .build();
        assignment = assignmentJpaRepository.save(assignment);
        assignmentId = assignment.getId();

        for (int f = 1; f <= ATTACHMENTS; f++) {
            assignment.getAttachmentFiles().add(AssignmentFileUpload.builder()
                    .assignment(assignment).uuid("uuid-" + f).fileName("file" + f + ".pdf")
                    .ord(f).img(false).fileSize(1024L).contentType("application/pdf")
                    .uploadedBy("선생님").referenceId(1L).build());
        }
        for (int s = 1; s <= STATUSES; s++) {
            assignment.getStudentSubmissionStatuses().add(StudentSubmissionStatus.builder()
                    .assignment(assignment).studentEmail(s + "@example.com").studentName("학생" + s)
                    .status(SubmissionStatus.NOT_SUBMITTED).submittedAt(null).build());
        }
        assignmentJpaRepository.save(assignment);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("컬렉션 두 개를 동시에 페치 조인하면 DB 가 M x N 행을 읽는다")
    void twoCollectionFetchJoinCausesCartesianProduct() {
        // Hibernate 6 는 엔티티 쿼리 결과를 자동으로 중복 제거하므로
        // JPQL 로는 행 폭발이 보이지 않는다. DB 가 실제로 만들어내는 행 수를 보려면
        // 페치 조인이 생성하는 것과 같은 조인을 네이티브 SQL 로 실행해야 한다.
        Number cartesianRows = (Number) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM assignment a " +
                        "LEFT JOIN assignment_file_upload f ON f.assignment_id = a.id " +
                        "LEFT JOIN student_submission_status s ON s.assignment_id = a.id " +
                        "WHERE a.id = :id")
                .setParameter("id", assignmentId)
                .getSingleResult();

        // 컬렉션을 하나씩 따로 조회했을 때의 행 수
        Number attachRows = (Number) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM assignment_file_upload WHERE assignment_id = :id")
                .setParameter("id", assignmentId).getSingleResult();
        Number statusRows = (Number) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM student_submission_status WHERE assignment_id = :id")
                .setParameter("id", assignmentId).getSingleResult();

        long cartesian = cartesianRows.longValue();
        long separate = attachRows.longValue() + statusRows.longValue() + 1;  // +1 = 과제 본체

        int expected = ATTACHMENTS * STATUSES;

        log.warn("[측정] 과제 1건 · 첨부 {}건 · 제출현황 {}건", ATTACHMENTS, STATUSES);
        log.warn("[측정] 컬렉션 2개 동시 페치 조인 -> DB 가 만드는 행 {} (= {} x {})",
                cartesian, ATTACHMENTS, STATUSES);
        log.warn("[측정] 컬렉션을 나눠 조회 -> 행 {} (= 1 + {} + {})",
                separate, ATTACHMENTS, STATUSES);
        log.warn("[측정] 읽는 행이 {}배", cartesian / (double) separate);

        assertThat(cartesian)
                .as("컬렉션 2개를 동시에 페치 조인하면 DB 는 M x N 행을 만든다")
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("상세 조회는 카테시안 곱 없이 컬렉션을 모두 채운다")
    void detailQueryLoadsCollectionsWithoutCartesianProduct() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        entityManager.flush();
        entityManager.clear();
        statistics.clear();

        AssignmentDTO dto = assignmentService.getAssignmentWithAllDetails(assignmentId);

        long queries = statistics.getPrepareStatementCount();
        log.warn("[측정] 상세 조회 쿼리 {}건 (과제 1 + 컬렉션 2 배치)", queries);

        // 데이터는 그대로 다 채워져야 한다
        assertThat(dto.getAttachmentFiles()).hasSize(ATTACHMENTS);
        assertThat(dto.getStudentSubmissionStatuses()).hasSize(STATUSES);

        // 과제 1건 + 컬렉션 2종 배치 로딩 = 3건 근처.
        // 컬렉션마다 개별 쿼리가 나가면 이 수를 넘어선다.
        assertThat(queries)
                .as("상세 조회에 실행된 쿼리 %d건", queries)
                .isLessThanOrEqualTo(5L);
    }
}
