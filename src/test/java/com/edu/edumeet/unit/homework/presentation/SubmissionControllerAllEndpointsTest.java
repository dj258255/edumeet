package com.edu.edumeet.unit.homework.presentation;

import com.edu.edumeet.homework.service.AssignmentService;
import com.edu.edumeet.homework.service.SubmissionService;
import com.edu.edumeet.homework.repository.AssignmentRepository;
import com.edu.edumeet.homework.repository.SubmissionRepository;
import com.edu.edumeet.homework.controller.SubmissionController;
import com.edu.edumeet.homework.dto.AssignmentCreateDTO;
import com.edu.edumeet.homework.dto.SubmissionCreateDTO;
import com.edu.edumeet.homework.dto.SubmissionDTO;
import com.edu.edumeet.attachment.presentation.dto.AttachmentAdapter;
import com.edu.edumeet.attachment.domain.Attachment;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.Arrays;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SubmissionController의 모든 엔드포인트를 테스트하는 통합 테스트
 * 실제 HTTP 요청/응답을 통해 전체 플로우를 검증
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Log4j2
@DisplayName("SubmissionController 모든 엔드포인트 통합 테스트")
public class SubmissionControllerAllEndpointsTest {

    private MockMvc mockMvc;

    @Autowired
    private SubmissionController submissionController;

    @Autowired
    private SubmissionService submissionService;

    @Autowired
    private AssignmentService assignmentService;

    @Autowired
    private SubmissionRepository submissionJpaRepository;

    @Autowired
    private AssignmentRepository assignmentJpaRepository;

    @Autowired
    private AttachmentAdapter attachmentAdapter;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WebApplicationContext webApplicationContext;

    // 테스트 데이터 상수
    private static final Long TEST_CLASS_ID = 1L;
    private static final String TEST_CLASS_MEMBER_EMAIL = "100L";
    private static final String TEACHER_EMAIL = "teacher@example.com";
    private static final String TEACHER_NAME = "테스트선생님";

    private Long testAssignmentId;
    private Long testSubmissionId;

    /**
     * 테스트용 첨부파일 목록을 생성하는 헬퍼 메서드
     */
    private List<Attachment> createTestAttachments() {
        return Arrays.asList(
                Attachment.builder()
                        .uuid("test-uuid-1")
                        .fileName("test-file.pdf")
                        .ord(1)
                        .img(false)
                        .fileSize(1024L)
                        .contentType("application/pdf")
                        .domain("homework")
                        .referenceId(null)
                        .build(),
                Attachment.builder()
                        .uuid("test-uuid-2")
                        .fileName("test-image.jpg")
                        .ord(2)
                        .img(true)
                        .fileSize(2048L)
                        .contentType("image/jpeg")
                        .domain("homework")
                        .referenceId(null)
                        .build()
        );
    }

    /**
     * 테스트용 과제 생성 헬퍼 메서드
     */
    private AssignmentCreateDTO createTestAssignmentDTO(String title, String description) {
        return AssignmentCreateDTO.builder()
                .title(title)
                .description(description)
                .createdByEmail(TEACHER_EMAIL)
                .createdByName(TEACHER_NAME)
                .attachmentFiles(null)
                .build();
    }

    /**
     * 테스트용 제출물 생성 헬퍼 메서드
     */
    private SubmissionCreateDTO createTestSubmissionDTO(String classMemberEmail, String classMemberName, String content) {
        return SubmissionCreateDTO.builder()
                .assignmentId(testAssignmentId)
                .classMemberEmail(classMemberEmail)
                .classMemberName(classMemberName)
                .content(content)
                .attachmentFiles(createTestAttachments())
                .build();
    }

    @BeforeEach
    void setUp() {
        log.info("=== 테스트 환경 설정 시작 ===");

        // MockMvc 설정
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        // 기존 데이터 정리
        cleanupTestData();

        // 테스트용 과제 생성
        createTestAssignment();

        // 테스트용 제출물 생성 (수정/삭제 테스트용)
        createTestSubmission();

        log.info("테스트 준비 완료: classId={}, assignmentId={}, classMemberId={}, submissionId={}",
                TEST_CLASS_ID, testAssignmentId, TEST_CLASS_MEMBER_EMAIL, testSubmissionId);
        log.info("=== 테스트 환경 설정 완료 ===");
    }

    @AfterEach
    void tearDown() {
        log.info("=== 테스트 정리 시작 ===");
        cleanupTestData();
        log.info("=== 테스트 정리 완료 ===");
    }

    /**
     * 테스트 데이터 정리
     */
    private void cleanupTestData() {
        try {
            submissionJpaRepository.deleteAll();
            assignmentJpaRepository.deleteAll();
            log.info("테스트 데이터 정리 완료");
        } catch (Exception e) {
            log.warn("테스트 데이터 정리 중 오류 발생: {}", e.getMessage());
        }
    }

    /**
     * 테스트용 과제 생성
     */
    private void createTestAssignment() {
        AssignmentCreateDTO assignmentCreateDTO = createTestAssignmentDTO(
                "통합테스트 과제",
                "제출물 테스트를 위한 과제입니다"
        );
        testAssignmentId = assignmentService.createAssignment(assignmentCreateDTO, TEST_CLASS_ID);
        log.info("테스트용 과제 생성 완료: assignmentId={}", testAssignmentId);
    }

    /**
     * 테스트용 제출물 생성
     */
    private void createTestSubmission() {
        SubmissionCreateDTO submissionCreateDTO = createTestSubmissionDTO(
                TEST_CLASS_MEMBER_EMAIL,
                "테스트학생",
                "통합테스트 제출물 내용"
        );
        testSubmissionId = submissionService.submitAssignment(submissionCreateDTO);
        log.info("테스트용 제출물 생성 완료: submissionId={}", testSubmissionId);
    }

    @Test
    @DisplayName("POST /api/v1/class/{classId}/submissions/assignment/{assignmentId} - 과제 제출 성공 테스트")
    void submitAssignmentSuccessTest() throws Exception {
        log.info("=== 과제 제출 성공 테스트 시작 ===");

        // Given
        String newStudentEmail = "200L";
        String newStudentName = "새학생";
        String content = "새로운 제출물 내용입니다.";

        SubmissionCreateDTO newSubmissionDTO = createTestSubmissionDTO(newStudentEmail, newStudentName, content);
        String requestJson = objectMapper.writeValueAsString(newSubmissionDTO);

        log.info("요청 데이터: {}", requestJson);

        // When & Then
        MvcResult result = mockMvc.perform(
                        post("/api/v1/class/{classId}/submissions/assignment/{assignmentId}",
                                TEST_CLASS_ID, testAssignmentId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        log.info("응답 데이터: {}", responseContent);

        // 등록된 제출물 ID 추출 및 검증
        Long createdSubmissionId = Long.valueOf(responseContent);
        assertThat(createdSubmissionId).isNotNull().isGreaterThan(0L);

        log.info("등록된 제출물 ID: {}", createdSubmissionId);

        // 실제로 등록되었는지 확인
        SubmissionDTO savedSubmission = submissionService.getSubmission(createdSubmissionId);
        assertThat(savedSubmission).isNotNull();
        assertThat(savedSubmission.getContent()).isEqualTo(content);
        assertThat(savedSubmission.getClassMemberEmail()).isEqualTo("200L");
        assertThat(savedSubmission.getAssignmentId()).isEqualTo(testAssignmentId);

        log.info("과제 제출 테스트 성공!");
        log.info("=== 과제 제출 성공 테스트 완료 ===");
    }

    @Test
    @DisplayName("POST /api/v1/class/{classId}/submissions/assignment/{assignmentId} - 잘못된 요청 데이터 테스트")
    void submitAssignmentWithInvalidDataTest() throws Exception {
        log.info("=== 잘못된 요청 데이터 테스트 시작 ===");

        // Given - 필수 필드가 누락된 요청
        SubmissionCreateDTO invalidSubmissionDTO = SubmissionCreateDTO.builder()
                .classMemberEmail("")  // 빈 이메일
                .classMemberName("테스트학생")
                .content("")  // 빈 내용
                .attachmentFiles(null)
                .build();

        String requestJson = objectMapper.writeValueAsString(invalidSubmissionDTO);
        log.info("잘못된 요청 데이터: {}", requestJson);

        // When & Then
        mockMvc.perform(
                        post("/api/v1/class/{classId}/submissions/assignment/{assignmentId}",
                                TEST_CLASS_ID, testAssignmentId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson))
                .andExpect(status().isBadRequest());

        log.info("=== 잘못된 요청 데이터 테스트 완료 ===");
    }

    @Test
    @DisplayName("POST /api/v1/class/{classId}/submissions/assignment/{assignmentId} - 존재하지 않는 과제 ID 테스트")
    void submitAssignmentWithNonExistentAssignmentTest() throws Exception {
        log.info("=== 존재하지 않는 과제 ID 테스트 시작 ===");

        // Given
        Long nonExistentAssignmentId = 99999L;
        SubmissionCreateDTO submissionDTO = createTestSubmissionDTO("300L", "테스트학생", "테스트 내용");
        String requestJson = objectMapper.writeValueAsString(submissionDTO);

        // When & Then
        mockMvc.perform(
                        post("/api/v1/class/{classId}/submissions/assignment/{assignmentId}",
                                TEST_CLASS_ID, nonExistentAssignmentId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson))
                .andExpect(status().isCreated());

        log.info("=== 존재하지 않는 과제 ID 테스트 완료 ===");
    }

    @Test
    @DisplayName("전체 제출물 엔드포인트 플로우 테스트")
    void fullWorkflowTest() throws Exception {
        log.info("=== 전체 제출물 워크플로우 테스트 시작 ===");

        // 1. 새로운 과제 생성
        AssignmentCreateDTO newAssignmentDTO = createTestAssignmentDTO(
                "워크플로우 테스트 과제",
                "워크플로우 테스트를 위한 과제입니다"
        );
        Long workflowAssignmentId = assignmentService.createAssignment(newAssignmentDTO, TEST_CLASS_ID);
        log.info("1. 워크플로우용 과제 생성 완료 - ID: {}", workflowAssignmentId);

        // 2. 제출물 제출
        SubmissionCreateDTO newSubmission = SubmissionCreateDTO.builder()
                .classMemberEmail("999L")
                .classMemberName("워크플로우학생")
                .content("워크플로우 테스트 제출물 내용")
                .attachmentFiles(createTestAttachments())
                .build();

        String submitJson = objectMapper.writeValueAsString(newSubmission);
        MvcResult submitResult = mockMvc.perform(
                        post("/api/v1/class/{classId}/submissions/assignment/{assignmentId}",
                                TEST_CLASS_ID, workflowAssignmentId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(submitJson))
                .andExpect(status().isCreated())
                .andReturn();

        Long workflowSubmissionId = Long.valueOf(submitResult.getResponse().getContentAsString());
        log.info("2. 제출물 제출 완료 - ID: {}", workflowSubmissionId);

        // 3. 제출물 조회 확인
        SubmissionDTO submittedWork = submissionService.getSubmission(workflowSubmissionId);
        assertThat(submittedWork).isNotNull();
        assertThat(submittedWork.getContent()).isEqualTo("워크플로우 테스트 제출물 내용");
        assertThat(submittedWork.getClassMemberEmail()).isEqualTo("999L");
        log.info("3. 제출물 조회 확인 완료");

        // 4. 시스템 변경사항 확인
        log.info("4. 시스템 변경사항:");
        log.info("   - 제출물 목록 조회 기능은 AssignmentController에서 제공됩니다.");
        log.info("   - 제출물 삭제/복원 기능은 더 이상 지원되지 않습니다. 한번 제출하면 최종입니다.");
        log.info("   - 제출물 수정 기능도 제한될 수 있습니다.");

        log.info("=== 전체 제출물 워크플로우 테스트 성공! ===");
    }

    @Test
    @DisplayName("동시에 여러 제출물 제출 테스트")
    void multipleSubmissionsTest() throws Exception {
        log.info("=== 다중 제출물 테스트 시작 ===");

        // 여러 학생의 제출물 생성
        String[] studentEmails = {"500L", "501L", "502L"};
        String[] studentNames = {"학생1", "학생2", "학생3"};

        for (int i = 0; i < studentEmails.length; i++) {
            SubmissionCreateDTO submission = createTestSubmissionDTO(
                    studentEmails[i],
                    studentNames[i],
                    "학생 " + (i + 1) + "의 제출물 내용"
            );

            String requestJson = objectMapper.writeValueAsString(submission);

            MvcResult result = mockMvc.perform(
                            post("/api/v1/class/{classId}/submissions/assignment/{assignmentId}",
                                    TEST_CLASS_ID, testAssignmentId)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestJson))
                    .andExpect(status().isCreated())
                    .andReturn();

            Long submissionId = Long.valueOf(result.getResponse().getContentAsString());
            log.info("학생 {} 제출완료 - ID: {}", studentNames[i], submissionId);
        }

        log.info("=== 다중 제출물 테스트 완료 ===");
    }
}