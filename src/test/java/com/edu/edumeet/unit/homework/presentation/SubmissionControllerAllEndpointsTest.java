package com.edu.edumeet.unit.homework.presentation;

import com.edu.edumeet.homework.infrastructure.AssignmentJpaRepository;
import com.edu.edumeet.homework.infrastructure.SubmissionJpaRepository;
import com.edu.edumeet.homework.presentation.AssignmentService;
import com.edu.edumeet.homework.presentation.SubmissionController;
import com.edu.edumeet.homework.presentation.SubmissionService;
import com.edu.edumeet.homework.presentation.dto.AssignmentCreateDTO;
import com.edu.edumeet.homework.presentation.dto.SubmissionCreateDTO;
import com.edu.edumeet.homework.presentation.dto.SubmissionDTO;
import com.edu.edumeet.upload.presentation.dto.FileUploadDTO;
import com.edu.edumeet.upload.presentation.dto.FileUploadAdapter;
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

import java.util.Collections;
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
    private FileUploadAdapter fileUploadAdapter;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private Long testClassId;
    private Long testAssignmentId;
    private Long testClassMemberId;
    private Long testSubmissionId;

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
                .content("통합테스트 제출물 내용")
                .build();
        
        testSubmissionId = submissionService.submitAssignment(submissionCreateDTO);
        
        log.info("테스트 준비 완료: classId={}, assignmentId={}, classMemberId={}, submissionId={}", 
                testClassId, testAssignmentId, testClassMemberId, testSubmissionId);
        log.info("=== 테스트 환경 설정 완료 ===");
    }

    @Test
    @DisplayName("[DEBUG_LOG] POST /api/v1/class/{classId}/submissions - 과제 제출 테스트")
    void submitAssignmentTest() throws Exception {
        log.info("=== 과제 제출 테스트 시작 ===");
        
        // Given
        SubmissionCreateDTO newSubmissionDTO = SubmissionCreateDTO.builder()
                .assignmentId(testAssignmentId)
                .classMemberId(200L)  // 다른 학생
                .content("새로운 제출물 내용입니다.")
                .build();

        String requestJson = objectMapper.writeValueAsString(newSubmissionDTO);
        log.info("[DEBUG_LOG] 요청 데이터: {}", requestJson);

        // When & Then
        MvcResult result = mockMvc.perform(post("/api/v1/class/{classId}/submissions", testClassId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(content().contentType("text/plain;charset=UTF-8"))
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
    @DisplayName("[DEBUG_LOG] GET /api/v1/class/{classId}/submissions/{id} - 제출물 조회 테스트")
    void getSubmissionTest() throws Exception {
        log.info("=== 제출물 조회 테스트 시작 ===");
        log.info("[DEBUG_LOG] 조회할 제출물 ID: {}", testSubmissionId);

        // When & Then
        MvcResult result = mockMvc.perform(get("/api/v1/class/{classId}/submissions/{id}", testClassId, testSubmissionId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(testSubmissionId))
                .andExpect(jsonPath("$.content").value("통합테스트 제출물 내용"))
                .andExpect(jsonPath("$.assignmentId").value(testAssignmentId))
                .andExpect(jsonPath("$.classMemberId").value(testClassMemberId))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        log.info("[DEBUG_LOG] 응답 데이터: {}", responseContent);
        
        log.info("[DEBUG_LOG] 제출물 조회 테스트 성공!");
        log.info("=== 제출물 조회 테스트 완료 ===");
    }

    @Test
    @DisplayName("[DEBUG_LOG] GET /api/v1/class/{classId}/submissions/assignment/{assignmentId} - 과제별 제출물 목록 조회 테스트")
    void getSubmissionsByAssignmentIdTest() throws Exception {
        log.info("=== 과제별 제출물 목록 조회 테스트 시작 ===");

        // Given - 추가 제출물 생성
        for (int i = 1; i <= 2; i++) {
            SubmissionCreateDTO additionalSubmission = SubmissionCreateDTO.builder()
                    .assignmentId(testAssignmentId)
                    .classMemberId(testClassMemberId + i)
                    .content("추가 제출물 " + i)
                    .build();
            submissionService.submitAssignment(additionalSubmission);
        }
        log.info("[DEBUG_LOG] 추가 제출물 2개 생성 완료");

        // 기대값 계산 - 기존 1개 + 추가 2개 = 총 3개
        int expectedTotalCount = 3;

        // When & Then
        MvcResult result = mockMvc.perform(get("/api/v1/class/{classId}/submissions/assignment/{assignmentId}", testClassId, testAssignmentId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(expectedTotalCount))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        log.info("[DEBUG_LOG] 응답 데이터: {}", responseContent);

        // 응답 데이터 상세 검증
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> submissionList = objectMapper.readValue(responseContent, List.class);
        
        // 제출물 내용들이 올바르게 포함되어 있는지 확인
        List<String> contents = submissionList.stream()
                .map(submission -> (String) submission.get("content"))
                .toList();
        
        assertThat(contents).contains("통합테스트 제출물 내용", "추가 제출물 1", "추가 제출물 2");
        
        log.info("[DEBUG_LOG] 과제별 제출물 목록 조회 테스트 성공! 총 {}개 제출물 조회됨", submissionList.size());
        log.info("=== 과제별 제출물 목록 조회 테스트 완료 ===");
    }

    @Test
    @DisplayName("[DEBUG_LOG] GET /api/v1/class/{classId}/submissions/class-member/{classMemberId} - 학생별 제출물 목록 조회 테스트")
    void getSubmissionsByClassMemberIdTest() throws Exception {
        log.info("=== 학생별 제출물 목록 조회 테스트 시작 ===");

        // Given - 같은 학생의 다른 과제 제출물 생성을 위해 추가 과제 생성
        AssignmentCreateDTO anotherAssignment = AssignmentCreateDTO.builder()
                .title("추가 과제")
                .description("학생별 제출물 테스트를 위한 추가 과제")
                .classId(testClassId)
                .createdById(10L)
                .createdByName("테스트선생님")
                .attachmentFiles(Collections.emptyList())
                .build();
        
        Long anotherAssignmentId = assignmentService.createAssignment(anotherAssignment);
        
        // 같은 학생이 다른 과제에 제출
        SubmissionCreateDTO anotherSubmission = SubmissionCreateDTO.builder()
                .assignmentId(anotherAssignmentId)
                .classMemberId(testClassMemberId)
                .content("같은 학생의 다른 과제 제출물")
                .build();
        submissionService.submitAssignment(anotherSubmission);
        
        log.info("[DEBUG_LOG] 같은 학생의 추가 제출물 생성 완료");

        // When & Then
        MvcResult result = mockMvc.perform(get("/api/v1/class/{classId}/submissions/class-member/{classMemberId}", testClassId, testClassMemberId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))  // 기존 1개 + 추가 1개
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        log.info("[DEBUG_LOG] 응답 데이터: {}", responseContent);
        
        log.info("[DEBUG_LOG] 학생별 제출물 목록 조회 테스트 성공!");
        log.info("=== 학생별 제출물 목록 조회 테스트 완료 ===");
    }

    @Test
    @DisplayName("[DEBUG_LOG] GET /api/v1/class/{classId}/submissions/assignment/{assignmentId}/class-member/{classMemberId} - 특정 과제의 특정 학생 제출물 조회 테스트")
    void getSubmissionByAssignmentAndClassMemberTest() throws Exception {
        log.info("=== 특정 과제의 특정 학생 제출물 조회 테스트 시작 ===");

        // When & Then
        MvcResult result = mockMvc.perform(get("/api/v1/class/{classId}/submissions/assignment/{assignmentId}/class-member/{classMemberId}", 
                        testClassId, testAssignmentId, testClassMemberId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.assignmentId").value(testAssignmentId))
                .andExpect(jsonPath("$.classMemberId").value(testClassMemberId))
                .andExpect(jsonPath("$.content").value("통합테스트 제출물 내용"))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        log.info("[DEBUG_LOG] 응답 데이터: {}", responseContent);
        
        log.info("[DEBUG_LOG] 특정 과제의 특정 학생 제출물 조회 테스트 성공!");
        log.info("=== 특정 과제의 특정 학생 제출물 조회 테스트 완료 ===");
    }

    @Test
    @DisplayName("[DEBUG_LOG] POST /api/v1/class/{classId}/submissions/{id}/files - 제출물 파일 추가 테스트")
    void addSubmissionFileTest() throws Exception {
        log.info("=== 제출물 파일 추가 테스트 시작 ===");
        log.info("[DEBUG_LOG] 대상 제출물 ID: {}", testSubmissionId);
        
        // Given
        FileUploadDTO fileUploadDTO = FileUploadDTO.builder()
                .uuid("test-submission-uuid-456")
                .fileName("homework_solution.docx")
                .ord(1)
                .img(false)
                .domain("submissions")
                .referenceId(testSubmissionId)
                .build();

        String requestJson = objectMapper.writeValueAsString(fileUploadDTO);
        log.info("[DEBUG_LOG] 요청 데이터: {}", requestJson);

        // When & Then
        MvcResult result = mockMvc.perform(post("/api/v1/class/{classId}/submissions/{id}/files", testClassId, testSubmissionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(content().string("파일이 성공적으로 추가되었습니다."))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        log.info("[DEBUG_LOG] 응답 데이터: {}", responseContent);
        
        log.info("[DEBUG_LOG] 제출물 파일 추가 테스트 성공!");
        log.info("=== 제출물 파일 추가 테스트 완료 ===");
    }

    @Test
    @DisplayName("[DEBUG_LOG] GET /api/v1/class/{classId}/submissions/{id}/with-files - 첨부파일 포함 제출물 조회 테스트")
    void getSubmissionWithFilesTest() throws Exception {
        log.info("=== 첨부파일 포함 제출물 조회 테스트 시작 ===");

        // When & Then
        MvcResult result = mockMvc.perform(get("/api/v1/class/{classId}/submissions/{id}/with-files", testClassId, testSubmissionId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(testSubmissionId))
                .andExpect(jsonPath("$.content").value("통합테스트 제출물 내용"))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        log.info("[DEBUG_LOG] 응답 데이터: {}", responseContent);
        
        log.info("[DEBUG_LOG] 첨부파일 포함 제출물 조회 테스트 성공!");
        log.info("=== 첨부파일 포함 제출물 조회 테스트 완료 ===");
    }

    @Test
    @DisplayName("[DEBUG_LOG] DELETE /api/v1/class/{classId}/submissions/{id} - 제출물 삭제 테스트")
    void deleteSubmissionTest() throws Exception {
        log.info("=== 제출물 삭제 테스트 시작 ===");
        log.info("[DEBUG_LOG] 삭제할 제출물 ID: {}", testSubmissionId);

        // 삭제 전 제출물 존재 확인
        SubmissionDTO beforeDelete = submissionService.getSubmission(testSubmissionId);
        assertThat(beforeDelete).isNotNull();
        log.info("[DEBUG_LOG] 삭제 전 제출물 내용: {}", beforeDelete.getContent());

        // When & Then
        MvcResult result = mockMvc.perform(delete("/api/v1/class/{classId}/submissions/{id}", testClassId, testSubmissionId))
                .andExpect(status().isOk())
                .andReturn();

        log.info("[DEBUG_LOG] 삭제 응답 상태: {}", result.getResponse().getStatus());

        // 삭제 후 조회 시도 (예외 발생 확인)
        try {
            submissionService.getSubmission(testSubmissionId);
            log.error("[DEBUG_LOG] 삭제된 제출물이 조회됨 - 테스트 실패!");
            throw new AssertionError("삭제된 제출물이 여전히 조회 가능합니다.");
        } catch (Exception e) {
            log.info("[DEBUG_LOG] 삭제된 제출물 조회 시 예외 발생 (정상): {}", e.getMessage());
        }

        log.info("[DEBUG_LOG] 제출물 삭제 테스트 성공!");
        log.info("=== 제출물 삭제 테스트 완료 ===");
    }

    @Test
    @DisplayName("[DEBUG_LOG] POST /api/v1/class/{classId}/submissions/{id}/restore - 제출물 복원 테스트")
    void restoreSubmissionTest() throws Exception {
        log.info("=== 제출물 복원 테스트 시작 ===");
        
        // 먼저 제출물을 삭제
        submissionService.deleteSubmission(testSubmissionId);
        log.info("[DEBUG_LOG] 제출물 {} 삭제 완료", testSubmissionId);

        // 삭제 확인
        try {
            submissionService.getSubmission(testSubmissionId);
            throw new AssertionError("제출물이 삭제되지 않았습니다.");
        } catch (Exception e) {
            log.info("[DEBUG_LOG] 제출물 삭제 확인: {}", e.getMessage());
        }

        // When & Then - 복원
        MvcResult result = mockMvc.perform(post("/api/v1/class/{classId}/submissions/{id}/restore", testClassId, testSubmissionId))
                .andExpect(status().isOk())
                .andReturn();

        log.info("[DEBUG_LOG] 복원 응답 상태: {}", result.getResponse().getStatus());

        // 복원 후 조회 확인
        SubmissionDTO restoredSubmission = submissionService.getSubmission(testSubmissionId);
        assertThat(restoredSubmission).isNotNull();
        assertThat(restoredSubmission.getContent()).isEqualTo("통합테스트 제출물 내용");
        
        log.info("[DEBUG_LOG] 복원된 제출물 내용: {}", restoredSubmission.getContent());
        log.info("[DEBUG_LOG] 제출물 복원 테스트 성공!");
        log.info("=== 제출물 복원 테스트 완료 ===");
    }

    @Test
    @DisplayName("전체 제출물 엔드포인트 플로우 테스트")
    void fullWorkflowTest() throws Exception {
        log.info("=== 전체 제출물 워크플로우 테스트 시작 ===");

        // 1. 제출물 제출
        SubmissionCreateDTO newSubmission = SubmissionCreateDTO.builder()
                .assignmentId(testAssignmentId)
                .classMemberId(999L)
                .content("워크플로우 테스트 제출물 내용")
                .build();

        String submitJson = objectMapper.writeValueAsString(newSubmission);
        MvcResult submitResult = mockMvc.perform(post("/api/v1/class/{classId}/submissions", testClassId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson))
                .andExpect(status().isCreated())
                .andReturn();

        Long workflowSubmissionId = Long.valueOf(submitResult.getResponse().getContentAsString());
        log.info("1. 제출물 제출 완료 - ID: {}", workflowSubmissionId);

        // 2. 제출물 조회
        mockMvc.perform(get("/api/v1/class/{classId}/submissions/{id}", testClassId, workflowSubmissionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("워크플로우 테스트 제출물 내용"));
        log.info("2. 제출물 조회 완료");

        // 3. 제출물 파일 추가
        FileUploadDTO fileDTO = FileUploadDTO.builder()
                .uuid("workflow-file-uuid")
                .fileName("workflow_file.pdf")
                .ord(1)
                .img(false)
                .domain("submissions")
                .referenceId(workflowSubmissionId)
                .build();
        String fileJson = objectMapper.writeValueAsString(fileDTO);
        mockMvc.perform(post("/api/v1/class/{classId}/submissions/{id}/files", testClassId, workflowSubmissionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fileJson))
                .andExpect(status().isOk());
        log.info("3. 제출물 파일 추가 완료");

        // 4. 제출물 삭제
        mockMvc.perform(delete("/api/v1/class/{classId}/submissions/{id}", testClassId, workflowSubmissionId))
                .andExpect(status().isOk());
        log.info("4. 제출물 삭제 완료");

        // 5. 제출물 복원
        mockMvc.perform(post("/api/v1/class/{classId}/submissions/{id}/restore", testClassId, workflowSubmissionId))
                .andExpect(status().isOk());
        log.info("5. 제출물 복원 완료");

        log.info("=== 전체 제출물 워크플로우 테스트 성공! ===");
    }
}