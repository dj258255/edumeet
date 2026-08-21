package com.edu.edumeet.unit.homework.presentation;

import com.edu.edumeet.homework.service.AssignmentService;
import com.edu.edumeet.attachment.domain.Attachment;
import com.edu.edumeet.homework.repository.AssignmentRepository;
import com.edu.edumeet.homework.controller.AssignmentController;
import com.edu.edumeet.homework.dto.AssignmentCreateDTO;
import com.edu.edumeet.homework.dto.AssignmentDTO;
import com.edu.edumeet.attachment.presentation.dto.AttachmentAdapter;
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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * AssignmentController의 모든 엔드포인트를 테스트하는 통합 테스트
 * 실제 HTTP 요청/응답을 통해 전체 플로우를 검증
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Log4j2
@DisplayName("AssignmentController 모든 엔드포인트 통합 테스트")
class AssignmentControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private AssignmentController assignmentController;

    @Autowired
    private AssignmentService assignmentService;

    @Autowired
    private AssignmentRepository assignmentJpaRepository;

    @Autowired
    private AttachmentAdapter attachmentAdapter;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private Long testClassId;
    private Long testAssignmentId;

    @BeforeEach
    void setUp() {
        log.info("=== 테스트 환경 설정 시작 ===");
        
        // MockMvc 설정
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        
        // 기존 데이터 정리
        assignmentJpaRepository.deleteAll();
        
        testClassId = 1L;
        
        // 테스트용 과제 생성 (수정/삭제 테스트용)
        AssignmentCreateDTO createDTO = AssignmentCreateDTO.builder()
                .title("통합테스트 과제")
                .description("통합테스트를 위한 과제입니다")
                .createdByEmail("teacher@example.com")
                .createdByName("테스트선생님")
                .attachmentFiles(null)
                .build();
        
        testAssignmentId = assignmentService.createAssignment(createDTO, testClassId);
        
        log.info("테스트 준비 완료: classId={}, assignmentId={}", testClassId, testAssignmentId);
        log.info("=== 테스트 환경 설정 완료 ===");
    }

    @Test
    @DisplayName("[DEBUG_LOG] 선생님이 파일과 함께 과제를 생성할 수 있다")
    void createAssignmentWithFiles() throws Exception {
        log.info("=== 파일이 포함된 과제 생성 테스트 시작 ===");
        
        // Given
        Attachment attachment = Attachment.builder()
                .uuid("test-uuid-123")
                .fileName("assignment_guide.pdf")
                .ord(1)
                .img(false)
                .fileSize(1024000L)
                .contentType("application/pdf")
                .uploadedBy("김선생")
                .referenceId(1L)
                .uploadedAt(LocalDateTime.now())
                .build();

        AssignmentCreateDTO createDTO = AssignmentCreateDTO.builder()
                .title("Spring Boot 과제")
                .description("Spring Boot를 활용한 REST API 개발")
                .createdByEmail("teacher@example.com")
                .createdByName("김선생")
                .attachmentFiles(null) // DTO는 AttachmentDTO 리스트를 받아야 함
                .build();

        String requestJson = objectMapper.writeValueAsString(createDTO);
        log.info("[DEBUG_LOG] 요청 데이터: {}", requestJson);

        // When & Then
        MvcResult result = mockMvc.perform(post("/api/v1/class/{classId}/assignments", testClassId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        log.info("[DEBUG_LOG] 응답 데이터: {}", responseContent);
        
        // 등록된 과제 ID 추출
        Long createdAssignmentId = Long.valueOf(responseContent);
        
        log.info("[DEBUG_LOG] 등록된 과제 ID: {}", createdAssignmentId);
        
        // 실제로 등록되었는지 확인
        AssignmentDTO savedAssignment = assignmentService.getAssignment(createdAssignmentId);
        assertThat(savedAssignment).isNotNull();
        assertThat(savedAssignment.getTitle()).isEqualTo("Spring Boot 과제");
        assertThat(savedAssignment.getCreatedByName()).isEqualTo("김선생");
        
        log.info("[DEBUG_LOG] 파일이 포함된 과제 생성 테스트 성공!");
        log.info("=== 파일이 포함된 과제 생성 테스트 완료 ===");
    }

    @Test
    @DisplayName("[DEBUG_LOG] 선생님이 파일 없이 과제를 생성할 수 있다")
    void createAssignmentWithoutFiles() throws Exception {
        log.info("=== 파일이 없는 과제 생성 테스트 시작 ===");
        
        // Given
        AssignmentCreateDTO createDTO = AssignmentCreateDTO.builder()
                .title("문제해결 과제")
                .description("알고리즘 문제 풀이")
                .createdByEmail("teacher@example.com")
                .createdByName("김선생")
                .attachmentFiles(null)
                .build();

        String requestJson = objectMapper.writeValueAsString(createDTO);
        log.info("[DEBUG_LOG] 요청 데이터: {}", requestJson);

        // When & Then
        MvcResult result = mockMvc.perform(post("/api/v1/class/{classId}/assignments", testClassId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        log.info("[DEBUG_LOG] 응답 데이터: {}", responseContent);
        
        Long createdAssignmentId = Long.valueOf(responseContent);
        log.info("[DEBUG_LOG] 등록된 과제 ID: {}", createdAssignmentId);
        
        // 실제로 등록되었는지 확인
        AssignmentDTO savedAssignment = assignmentService.getAssignment(createdAssignmentId);
        assertThat(savedAssignment).isNotNull();
        assertThat(savedAssignment.getTitle()).isEqualTo("문제해결 과제");
        assertThat(savedAssignment.getAttachmentFiles()).isNullOrEmpty();
        
        log.info("[DEBUG_LOG] 파일 없는 과제 생성 테스트 성공!");
        log.info("=== 파일 없는 과제 생성 테스트 완료 ===");
    }

    @Test
    @DisplayName("[DEBUG_LOG] 과제 조회 시 첨부파일이 함께 조회된다")
    void getAssignmentWithAttachmentFiles() throws Exception {
        log.info("=== 과제 조회 테스트 시작 ===");
        log.info("[DEBUG_LOG] 조회할 과제 ID: {}", testAssignmentId);

        // When & Then
        MvcResult result = mockMvc.perform(get("/api/v1/class/{classId}/assignments/{id}", testClassId, testAssignmentId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(testAssignmentId))
                .andExpect(jsonPath("$.title").value("통합테스트 과제"))
                .andExpect(jsonPath("$.description").value("통합테스트를 위한 과제입니다"))
                .andExpect(jsonPath("$.createdByName").value("테스트선생님"))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        log.info("[DEBUG_LOG] 응답 데이터: {}", responseContent);
        
        log.info("[DEBUG_LOG] 과제 조회 테스트 성공!");
        log.info("=== 과제 조회 테스트 완료 ===");
    }


    @Test
    @DisplayName("[DEBUG_LOG] 클래스별 과제 목록을 조회할 수 있다")
    void getAssignmentsByClassId() throws Exception {
        log.info("=== 클래스별 과제 목록 조회 테스트 시작 ===");

        // Given - 추가 테스트 데이터 생성
        for (int i = 1; i <= 2; i++) {
            AssignmentCreateDTO additionalAssignment = AssignmentCreateDTO.builder()
                    .title("추가 과제 " + i)
                    .description("추가 과제 설명 " + i)
                    .createdByEmail("teacher@example.com")
                    .createdByName("테스트선생님" + i)
                    .attachmentFiles(Collections.emptyList())
                    .build();
            assignmentService.createAssignment(additionalAssignment, testClassId);
        }
        log.info("[DEBUG_LOG] 추가 테스트 데이터 2개 생성 완료");

        // 기대값 계산 - 기존 1개 + 추가 2개 = 총 3개
        int expectedTotalCount = 3;

        // When & Then
        MvcResult result = mockMvc.perform(get("/api/v1/class/{classId}/assignments", testClassId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(expectedTotalCount))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        log.info("[DEBUG_LOG] 응답 데이터: {}", responseContent);

        // 응답 데이터 상세 검증
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assignmentList = objectMapper.readValue(responseContent, List.class);
        
        // 과제 제목들이 올바르게 포함되어 있는지 확인
        List<String> titles = assignmentList.stream()
                .map(assignment -> (String) assignment.get("title"))
                .toList();
        
        assertThat(titles).contains("통합테스트 과제", "추가 과제 1", "추가 과제 2");
        
        log.info("[DEBUG_LOG] 클래스별 과제 목록 조회 테스트 성공! 총 {}개 과제 조회됨", assignmentList.size());
        log.info("=== 클래스별 과제 목록 조회 테스트 완료 ===");
    }
}