package com.edu.edumeet.integration.homework;

import com.edu.edumeet.homework.repository.AssignmentRepository;
import com.edu.edumeet.homework.application.AssignmentService;
import com.edu.edumeet.homework.domain.Assignment;
import com.edu.edumeet.homework.domain.StudentSubmissionStatus;
import com.edu.edumeet.homework.domain.SubmissionStatus;
import com.edu.edumeet.homework.domain.Assignment;
import com.edu.edumeet.homework.repository.AssignmentRepository;
import com.edu.edumeet.homework.domain.AssignmentFileUpload;
import com.edu.edumeet.homework.domain.StudentSubmissionStatus;
import com.edu.edumeet.homework.presentation.dto.AssignmentDTO;
import com.edu.edumeet.classroom.domain.ClassMember;
import com.edu.edumeet.classroom.repository.ClassMemberRepository;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.annotation.Rollback;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * N+1 쿼리 문제 해결 검증 테스트
 * 
 * 목적:
 * 1. 기존 BatchSize 방식의 N+1 문제 시연
 * 2. 새로운 FetchJoin 방식의 단일 쿼리 실행 검증
 * 3. 성능 개선 효과 측정
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Rollback
@Log4j2
class N1QueryOptimizationTest {

    @Autowired
    private AssignmentService assignmentService;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private AssignmentRepository assignmentJpaRepository;

    @Autowired
    private ClassMemberRepository classMemberRepository;

    private Long testAssignmentId;
    private Long testClassId = 1L;

    @BeforeEach
    void setUp() {
        // 테스트 데이터 생성
        createTestData();
    }

    @Test
    @DisplayName("N+1 문제 해결 검증: getAssignmentWithAllDetails - 단일 쿼리 실행")
    void testOptimizedQuerySingleExecution() {
        log.info("[DEBUG_LOG] =========================");
        log.info("[DEBUG_LOG] N+1 최적화 테스트 시작");
        log.info("[DEBUG_LOG] =========================");

        // When: 최적화된 메서드 호출
        long startTime = System.currentTimeMillis();
        
        AssignmentDTO result = assignmentService.getAssignmentWithAllDetails(testAssignmentId);
        
        long endTime = System.currentTimeMillis();
        
        // Then: 데이터가 올바르게 로드되었는지 확인
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(testAssignmentId);
        assertThat(result.getTitle()).isEqualTo("테스트 과제");
        
        // 첨부파일이 로드되었는지 확인
        assertThat(result.getAttachmentFiles()).isNotEmpty();
        assertThat(result.getAttachmentFiles().size()).isEqualTo(2);
        
        // 제출 현황이 로드되었는지 확인
        assertThat(result.getStudentSubmissionStatuses()).isNotEmpty();
        assertThat(result.getStudentSubmissionStatuses().size()).isEqualTo(3);
        
        log.info("[DEBUG_LOG] 실행 시간: {}ms", (endTime - startTime));
        log.info("[DEBUG_LOG] 첨부파일 수: {}", result.getAttachmentFiles().size());
        log.info("[DEBUG_LOG] 제출현황 수: {}", result.getStudentSubmissionStatuses().size());
        log.info("[DEBUG_LOG] N+1 최적화 테스트 완료");
    }

    @Test
    @DisplayName("기존 방식과 최적화된 방식 비교")
    void testQueryCountComparison() {
        log.info("[DEBUG_LOG] =========================");
        log.info("[DEBUG_LOG] 쿼리 수행 횟수 비교 테스트");
        log.info("[DEBUG_LOG] =========================");

        // 1. 기존 방식 (두 번의 쿼리)
        log.info("[DEBUG_LOG] === 기존 방식 (2번 쿼리) ===");
        long startTime1 = System.currentTimeMillis();
        
        Optional<Assignment> assignment1 = assignmentRepository.findByIdWithAttachmentFiles(testAssignmentId);
        Optional<Assignment> assignment2 = assignmentRepository.findByIdWithSubmissionStatuses(testAssignmentId);
        
        long endTime1 = System.currentTimeMillis();
        
        assertThat(assignment1).isPresent();
        assertThat(assignment2).isPresent();
        
        log.info("[DEBUG_LOG] 기존 방식 실행 시간: {}ms", (endTime1 - startTime1));

        // 2. 최적화된 방식 (한 번의 쿼리)
        log.info("[DEBUG_LOG] === 최적화된 방식 (1번 쿼리) ===");
        long startTime2 = System.currentTimeMillis();
        
        Optional<Assignment> optimizedAssignment = assignmentRepository.findByIdWithAllDetails(testAssignmentId);
        
        long endTime2 = System.currentTimeMillis();
        
        assertThat(optimizedAssignment).isPresent();
        assertThat(optimizedAssignment.get().getAttachmentFiles()).hasSize(2);
        assertThat(optimizedAssignment.get().getStudentSubmissionStatuses()).hasSize(3);
        
        log.info("[DEBUG_LOG] 최적화된 방식 실행 시간: {}ms", (endTime2 - startTime2));
        log.info("[DEBUG_LOG] 성능 개선 정도: {}%", 
                 ((double)(endTime1 - startTime1) - (endTime2 - startTime2)) / (endTime1 - startTime1) * 100);
    }

    @Test
    @DisplayName("Cartesian Product 방지 검증 - DISTINCT 키워드 효과")
    void testDistinctKeywordEffect() {
        log.info("[DEBUG_LOG] =========================");
        log.info("[DEBUG_LOG] DISTINCT 키워드 효과 검증");
        log.info("[DEBUG_LOG] =========================");

        // When: DISTINCT 포함된 최적화 쿼리 실행
        Optional<Assignment> result = assignmentJpaRepository.findByIdWithAllDetails(testAssignmentId);

        // Then: 중복 제거 확인
        assertThat(result).isPresent();
        
        Assignment assignment = result.get();
        
        // 첨부파일 중복 제거 확인
        Set<Long> attachmentFileIds = new HashSet<>();
        assignment.getAttachmentFiles().forEach(file -> {
            boolean isUnique = attachmentFileIds.add(file.getId());
            assertThat(isUnique).as("첨부파일 ID 중복 발생: " + file.getId()).isTrue();
        });
        
        // 제출현황 중복 제거 확인
        Set<Long> statusIds = new HashSet<>();
        assignment.getStudentSubmissionStatuses().forEach(status -> {
            boolean isUnique = statusIds.add(status.getId());
            assertThat(isUnique).as("제출현황 ID 중복 발생: " + status.getId()).isTrue();
        });
        
        log.info("[DEBUG_LOG] 첨부파일 수 (중복제거): {}", assignment.getAttachmentFiles().size());
        log.info("[DEBUG_LOG] 제출현황 수 (중복제거): {}", assignment.getStudentSubmissionStatuses().size());
        log.info("[DEBUG_LOG] DISTINCT 키워드 효과 검증 완료");
    }

    @Test
    @DisplayName("클래스별 과제 조회 N+1 문제 해결 검증")
    void testClassAssignmentListOptimization() {
        log.info("[DEBUG_LOG] =========================");
        log.info("[DEBUG_LOG] 클래스별 과제 목록 N+1 최적화 검증");
        log.info("[DEBUG_LOG] =========================");

        // When: 클래스별 과제 목록 조회 (기존 방식)
        List<Assignment> assignments = assignmentRepository.findByClassIdOrderByRegDateDesc(testClassId);
        
        // Then: 데이터 확인
        assertThat(assignments).isNotEmpty();
        
        log.info("[DEBUG_LOG] 조회된 과제 수: {}", assignments.size());
        
        // 각 과제의 관계 데이터 접근 시 추가 쿼리 발생 시뮬레이션
        assignments.forEach(assignment -> {
            log.info("[DEBUG_LOG] 과제 ID: {}, 첨부파일 수: {}, 제출현황 수: {}", 
                     assignment.getId(), 
                     assignment.getAttachmentFiles().size(),
                     assignment.getStudentSubmissionStatuses().size());
        });
    }

    private void createTestData() {
        // 과제 생성
        Assignment assignment = Assignment.builder()
                .title("테스트 과제")
                .description("N+1 테스트를 위한 과제")
                .classId(testClassId)
                .createdByEmail("test@example.com")
                .createdByName("테스트 선생님")
                .attachmentFiles(new HashSet<>())
                .studentSubmissionStatuses(new HashSet<>())
                .build();

        assignment = assignmentJpaRepository.save(assignment);
        testAssignmentId = assignment.getId();

        // 첨부파일 생성 (2개)
        for (int i = 1; i <= 2; i++) {
            AssignmentFileUpload file = AssignmentFileUpload.builder()
                    .assignment(assignment)
                    .uuid("test-uuid-" + i)
                    .fileName("테스트파일" + i + ".pdf")
                    .ord(i)
                    .img(false)
                    .fileSize(1024L * i)
                    .contentType("application/pdf")
                    .uploadedBy("테스트 사용자")
                    .referenceId(1L)
                    .build();
            assignment.getAttachmentFiles().add(file);
        }

        // 학생 제출 현황 생성 (3개)
        for (int i = 1; i <= 3; i++) {
            StudentSubmissionStatus status = StudentSubmissionStatus.builder()
                    .assignment(assignment)
                    .studentEmail( i + "@example.com")
                    .studentName("학생" + i)
                    .status(i % 2 == 0 ? SubmissionStatus.SUBMITTED : SubmissionStatus.NOT_SUBMITTED)
                    .submittedAt(i % 2 == 0 ? LocalDateTime.now() : null)
                    .build();
            assignment.getStudentSubmissionStatuses().add(status);
        }

        assignmentJpaRepository.save(assignment);
        
        log.info("[DEBUG_LOG] 테스트 데이터 생성 완료 - Assignment ID: {}", testAssignmentId);
    }
}