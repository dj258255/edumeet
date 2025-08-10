package com.edu.edumeet.unit.homework.presentation;

import com.edu.edumeet.homework.infrastructure.AssignmentJpaRepository;
import com.edu.edumeet.homework.infrastructure.SubmissionJpaRepository;
import com.edu.edumeet.homework.presentation.AssignmentService;
import com.edu.edumeet.homework.presentation.SubmissionController;
import com.edu.edumeet.homework.presentation.SubmissionService;
import com.edu.edumeet.homework.presentation.dto.AssignmentCreateDTO;
import com.edu.edumeet.homework.presentation.dto.SubmissionCreateDTO;
import com.edu.edumeet.homework.presentation.dto.SubmissionDTO;
import com.edu.edumeet.attachment.presentation.dto.AttachmentAdapter;
import com.edu.edumeet.attachment.domain.Attachment;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

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
    private SubmissionJpaRepository submissionJpaRepository;

    @Autowired
    private AssignmentJpaRepository assignmentJpaRepository;

    @Autowired
    private AttachmentAdapter attachmentAdapter;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private Long testClassId;
    private Long testAssignmentId;
    private Long testClassMemberId;
    private Long testSubmissionId;

    /**
     * 테스트용 첨부파일 목록을 생성하는 헬퍼 메서드
     */
    private List<Attachment> createTestAttachments() {
        return List.of(
            Attachment.builder()
                .uuid("test-uuid-1")
                .fileName("test-file.pdf")
                .ord(1)
                .img(false)
                .fileSize(1024L)
                .contentType("application/pdf")
                .domain("homework")
                .referenceId(null)
                .build()
        );
    }

    @BeforeEach
    void setUp() {
        log.info("=== 테스트 환경 설정 시작 ===");
        
        // MockMvc 설정
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        
        // 기존 데이터 정리
        submissionJpaRepository.deleteAll();
        assignmentJpaRepository.deleteAll();
        
        testClassId = 1L;
        testClassMemberId = 100L;
        
        // 테스트용 과제 생성 (제출물 테스트용)
        AssignmentCreateDTO assignmentCreateDTO = AssignmentCreateDTO.builder()
                .title("통합테스트 과제")
                .description("제출물 테스트를 위한 과제입니다")
                .classId(testClassId)
                .createdById(10L)
                .createdByName("테스트선생님")
                .attachmentFiles(null)
                .build();
        
        testAssignmentId = assignmentService.createAssignment(assignmentCreateDTO);
        
        // 테스트용 제출물 생성 (수정/삭제 테스트용)
        SubmissionCreateDTO submissionCreateDTO = SubmissionCreateDTO.builder()
                .assignmentId(testAssignmentId)
                .classMemberId(testClassMemberId)
                .classMemberName("테스트학생")
                .content("통합테스트 제출물 내용")
                .attachmentFiles(createTestAttachments())
                .build();
        
        testSubmissionId = submissionService.submitAssignment(submissionCreateDTO);
        
        log.info("테스트 준비 완료: classId={}, assignmentId={}, classMemberId={}, submissionId={}", 
                testClassId, testAssignmentId, testClassMemberId, testSubmissionId);
        log.info("=== 테스트 환경 설정 완료 ===");
    }

    @Test
    @DisplayName("[DEBUG_LOG] POST /api/v1/class/{classId}/submissions/assignment/{assignmentId} - 과제 제출 테스트")
    void submitAssignmentTest() throws Exception {
        log.info("=== 과제 제출 테스트 시작 ===");
        
        // Given
        SubmissionCreateDTO newSubmissionDTO = SubmissionCreateDTO.builder()
                .classMemberId(200L)  // 다른 학생
                .classMemberName("새학생")
                .content("새로운 제출물 내용입니다.")
                .attachmentFiles(createTestAttachments())
                .build();

        String requestJson = objectMapper.writeValueAsString(newSubmissionDTO);
        log.info("[DEBUG_LOG] 요청 데이터: {}", requestJson);

        // When & Then
        MvcResult result = mockMvc.perform(post("/api/v1/class/{classId}/submissions/assignment/{assignmentId}", testClassId, testAssignmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        log.info("[DEBUG_LOG] 응답 데이터: {}", responseContent);
        
        // 등록된 제출물 ID 추출
        Long createdSubmissionId = Long.valueOf(responseContent);
        
        log.info("[DEBUG_LOG] 등록된 제출물 ID: {}", createdSubmissionId);
        
        // 실제로 등록되었는지 확인
        SubmissionDTO savedSubmission = submissionService.getSubmission(createdSubmissionId);
        assertThat(savedSubmission).isNotNull();
        assertThat(savedSubmission.getContent()).isEqualTo("새로운 제출물 내용입니다.");
        assertThat(savedSubmission.getClassMemberId()).isEqualTo(200L);
        
        log.info("[DEBUG_LOG] 과제 제출 테스트 성공!");
        log.info("=== 과제 제출 테스트 완료 ===");
    }








    @Test
    @DisplayName("전체 제출물 엔드포인트 플로우 테스트")
    void fullWorkflowTest() throws Exception {
        log.info("=== 전체 제출물 워크플로우 테스트 시작 ===");

        // 1. 제출물 제출
        SubmissionCreateDTO newSubmission = SubmissionCreateDTO.builder()
                .classMemberId(999L)
                .classMemberName("워크플로우학생")
                .content("워크플로우 테스트 제출물 내용")
                .attachmentFiles(createTestAttachments())
                .build();

        String submitJson = objectMapper.writeValueAsString(newSubmission);
        MvcResult submitResult = mockMvc.perform(post("/api/v1/class/{classId}/submissions/assignment/{assignmentId}", testClassId, testAssignmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson))
                .andExpect(status().isCreated())
                .andReturn();

        Long workflowSubmissionId = Long.valueOf(submitResult.getResponse().getContentAsString());
        log.info("1. 제출물 제출 완료 - ID: {}", workflowSubmissionId);

        // 2. 제출물 목록 조회 기능은 AssignmentController로 이관됨
        log.info("2. 제출물 목록 조회 기능은 AssignmentController에서 제공됩니다.");

        // 제출물 삭제/복원 기능은 제거됨 (한번 제출하면 끝)
        log.info("제출물 삭제/복원 기능은 더 이상 지원되지 않습니다. 한번 제출하면 최종입니다.");

        log.info("=== 전체 제출물 워크플로우 테스트 성공! ===");
    }
}