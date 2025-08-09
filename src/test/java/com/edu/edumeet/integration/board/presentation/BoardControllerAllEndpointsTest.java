package com.edu.edumeet.integration.board.presentation;

import com.edu.edumeet.board.domain.Board;
import com.edu.edumeet.board.domain.BoardCategory;
import com.edu.edumeet.board.infrastructure.BoardCategoryJpaEntity;
import com.edu.edumeet.board.infrastructure.BoardCategoryJpaRepository;
import com.edu.edumeet.board.infrastructure.BoardJpaEntity;
import com.edu.edumeet.board.infrastructure.BoardJpaRepository;
import com.edu.edumeet.board.presentation.BoardController;
import com.edu.edumeet.board.presentation.BoardService;
import com.edu.edumeet.board.presentation.dto.BoardDTO;
import com.edu.edumeet.board.presentation.dto.PageRequestDTO;
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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * BoardController의 모든 엔드포인트를 테스트하는 통합 테스트
 * 실제 HTTP 요청/응답을 통해 전체 플로우를 검증
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Log4j2
@DisplayName("BoardController 모든 엔드포인트 통합 테스트")
public class BoardControllerAllEndpointsTest {

    private MockMvc mockMvc;

    @Autowired
    private BoardController boardController;

    @Autowired
    private BoardService boardService;

    @Autowired
    private BoardJpaRepository boardJpaRepository;

    @Autowired
    private BoardCategoryJpaRepository boardCategoryJpaRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private Long testClassId;
    private Long testCategoryId;
    private Long testBoardId;
    private BoardDTO testBoardDTO;

    @BeforeEach
    void setUp() {
        log.info("=== 테스트 환경 설정 시작 ===");
        
        // MockMvc 설정
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        
        // 기존 데이터 정리
        boardJpaRepository.deleteAll();
        boardCategoryJpaRepository.deleteAll();
        
        testClassId = 1L;
        
        // 테스트용 카테고리 생성
        BoardCategory category = BoardCategory.builder()
                .categoryName("통합테스트 카테고리")
                .classId(testClassId)
                .createdBy("tester")
                .build();
        
        BoardCategoryJpaEntity savedCategory = boardCategoryJpaRepository.save(BoardCategoryJpaEntity.fromDomain(category));
        testCategoryId = savedCategory.getId();
        
        // 테스트용 게시글 생성 (수정/삭제/좋아요/싫어요 테스트용)
        testBoardDTO = BoardDTO.builder()
                .title("테스트 게시글")
                .content("테스트 내용")
                .writer("tester")
                .classId(testClassId)
                .categoryId(testCategoryId)
                .boardType("NORMAL")
                .build();
        
        testBoardId = boardService.register(testBoardDTO);
        
        log.info("테스트 준비 완료: classId={}, categoryId={}, boardId={}", testClassId, testCategoryId, testBoardId);
        log.info("=== 테스트 환경 설정 완료 ===");
    }

    @Test
    @DisplayName("POST /api/v1/class/{classId}/boards - 게시글 등록 테스트")
    void registerBoardTest() throws Exception {
        log.info("=== 게시글 등록 테스트 시작 ===");
        
        // Given
        BoardDTO newBoardDTO = BoardDTO.builder()
                .title("새로운 게시글")
                .content("새로운 게시글 내용입니다.")
                .writer("newTester")
                .categoryId(testCategoryId)
                .boardType("NORMAL")
                .build();

        String requestJson = objectMapper.writeValueAsString(newBoardDTO);
        log.info("요청 데이터: {}", requestJson);

        // When & Then
        MvcResult result = mockMvc.perform(post("/api/v1/class/{classId}/boards", testClassId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").exists())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        log.info("응답 데이터: {}", responseContent);
        
        // 등록된 게시글 ID 추출
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> response = objectMapper.readValue(responseContent, java.util.Map.class);
        Integer boardIdInt = (Integer) response.get("id");
        Long registeredBoardId = boardIdInt.longValue();
        
        log.info("등록된 게시글 ID: {}", registeredBoardId);
        
        // 실제로 등록되었는지 확인
        BoardDTO savedBoard = boardService.readOne(registeredBoardId);
        assertThat(savedBoard).isNotNull();
        assertThat(savedBoard.getTitle()).isEqualTo("새로운 게시글");
        assertThat(savedBoard.getWriter()).isEqualTo("newTester");
        
        log.info("게시글 등록 테스트 성공!");
        log.info("=== 게시글 등록 테스트 완료 ===");
    }

    @Test
    @DisplayName("GET /api/v1/class/{classId}/boards/{id} - 게시글 조회 테스트")
    void readBoardTest() throws Exception {
        log.info("=== 게시글 조회 테스트 시작 ===");
        log.info("조회할 게시글 ID: {}", testBoardId);

        // When & Then
        MvcResult result = mockMvc.perform(get("/api/v1/class/{classId}/boards/{id}", testClassId, testBoardId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(testBoardId))
                .andExpect(jsonPath("$.title").value("테스트 게시글"))
                .andExpect(jsonPath("$.content").value("테스트 내용"))
                .andExpect(jsonPath("$.writer").value("tester"))
                .andExpect(jsonPath("$.view").value(1)) // 조회수 증가 확인
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        log.info("응답 데이터: {}", responseContent);
        
        log.info("게시글 조회 테스트 성공!");
        log.info("=== 게시글 조회 테스트 완료 ===");
    }

    @Test
    @DisplayName("PUT /api/v1/class/{classId}/boards/{id} - 게시글 수정 테스트")
    void modifyBoardTest() throws Exception {
        log.info("=== 게시글 수정 테스트 시작 ===");
        log.info("수정할 게시글 ID: {}", testBoardId);

        // Given
        BoardDTO modifiedBoardDTO = BoardDTO.builder()
                .id(testBoardId)
                .title("수정된 게시글 제목")
                .content("수정된 게시글 내용입니다.")
                .writer("tester")
                .categoryId(testCategoryId)
                .boardType("NORMAL")
                .build();

        String requestJson = objectMapper.writeValueAsString(modifiedBoardDTO);
        log.info("수정 요청 데이터: {}", requestJson);

        // When & Then
        MvcResult result = mockMvc.perform(put("/api/v1/class/{classId}/boards/{id}", testClassId, testBoardId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.result").value("수정 완료"))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        log.info("응답 데이터: {}", responseContent);

        // 실제로 수정되었는지 확인
        BoardDTO updatedBoard = boardService.readOne(testBoardId);
        assertThat(updatedBoard.getTitle()).isEqualTo("수정된 게시글 제목");
        assertThat(updatedBoard.getContent()).isEqualTo("수정된 게시글 내용입니다.");
        
        log.info("게시글 수정 확인 - 제목: {}, 내용: {}", updatedBoard.getTitle(), updatedBoard.getContent());
        log.info("게시글 수정 테스트 성공!");
        log.info("=== 게시글 수정 테스트 완료 ===");
    }

    @Test
    @DisplayName("POST /api/v1/class/{classId}/boards/{id}/favorite - 좋아요 증가 테스트")
    void favoriteTest() throws Exception {
        log.info("=== 좋아요 증가 테스트 시작 ===");
        log.info("대상 게시글 ID: {}", testBoardId);

        // 초기 좋아요 수 확인
        BoardDTO initialBoard = boardService.readOne(testBoardId);
        long initialFavorites = initialBoard.getFavorite();
        log.info("초기 좋아요 수: {}", initialFavorites);

        // When & Then - 첫 번째 좋아요 (추가)
        MvcResult result1 = mockMvc.perform(post("/api/v1/class/{classId}/boards/{id}/favorite", testClassId, testBoardId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.favoriteCount").value(initialFavorites + 1))
                .andReturn();

        String responseContent1 = result1.getResponse().getContentAsString();
        log.info("첫 번째 좋아요 후 응답: {}", responseContent1);

        // 중간 상태 확인 - DB에서 실제 좋아요 수 확인
        BoardDTO middleBoard = boardService.readOne(testBoardId);
        log.info("첫 번째 좋아요 후 DB 좋아요 수: {}", middleBoard.getFavorite());

        // 두 번째 좋아요 (추가 증가)
        MvcResult result2 = mockMvc.perform(post("/api/v1/class/{classId}/boards/{id}/favorite", testClassId, testBoardId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.favoriteCount").value(initialFavorites + 2))
            .andReturn();

        String responseContent2 = result2.getResponse().getContentAsString();
        log.info("두 번째 좋아요 후 응답 (추가 증가): {}", responseContent2);

        // 최종 상태 확인 - DB에서 실제 좋아요 수 확인
        BoardDTO finalBoard = boardService.readOne(testBoardId);
        log.info("두 번째 좋아요 후 DB 좋아요 수: {}", finalBoard.getFavorite());
        
        // 최종 검증 - 두 번 클릭으로 2 증가
        assertThat(finalBoard.getFavorite()).isEqualTo(initialFavorites + 2);

        log.info("좋아요 증가 테스트 성공!");
        log.info("=== 좋아요 증가 테스트 완료 ===");
    }

    @Test
    @DisplayName("POST /api/v1/class/{classId}/boards/{id}/dislike - 싫어요 토글 테스트")
    void toggleDislikeTest() throws Exception {
        log.info("=== 싫어요 토글 테스트 시작 ===");
        log.info("대상 게시글 ID: {}", testBoardId);

        // 초기 싫어요 수 확인
        BoardDTO initialBoard = boardService.readOne(testBoardId);
        long initialDislikes = initialBoard.getDislike();
        log.info("초기 싫어요 수: {}", initialDislikes);

        // When & Then - 첫 번째 싫어요
        MvcResult result1 = mockMvc.perform(post("/api/v1/class/{classId}/boards/{id}/dislike", testClassId, testBoardId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.dislikeCount").value(initialDislikes + 1))
                .andReturn();

        String responseContent1 = result1.getResponse().getContentAsString();
        log.info("첫 번째 싫어요 후 응답: {}", responseContent1);

        // 두 번째 싫어요 (추가 증가 - 취소 기능 없음)
        MvcResult result2 = mockMvc.perform(post("/api/v1/class/{classId}/boards/{id}/dislike", testClassId, testBoardId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.dislikeCount").value(initialDislikes + 2))
                .andReturn();

        String responseContent2 = result2.getResponse().getContentAsString();
        log.info("두 번째 싫어요 후 응답 (추가 증가): {}", responseContent2);

        log.info("싫어요 토글 테스트 성공!");
        log.info("=== 싫어요 토글 테스트 완료 ===");
    }

    @Test
    @DisplayName("DELETE /api/v1/class/{classId}/boards/{id} - 게시글 삭제 테스트")
    void removeBoardTest() throws Exception {
        log.info("=== 게시글 삭제 테스트 시작 ===");
        log.info("삭제할 게시글 ID: {}", testBoardId);

        // 삭제 전 게시글 존재 확인
        BoardDTO beforeDelete = boardService.readOne(testBoardId);
        assertThat(beforeDelete).isNotNull();
        log.info("삭제 전 게시글 제목: {}", beforeDelete.getTitle());

        // When & Then
        MvcResult result = mockMvc.perform(delete("/api/v1/class/{classId}/boards/{id}", testClassId, testBoardId))
                .andExpect(status().isNoContent())
                .andReturn();

        log.info("삭제 응답 상태: {}", result.getResponse().getStatus());

        // 삭제 후 조회 시도 (예외 발생 확인)
        try {
            boardService.readOne(testBoardId);
            log.error("삭제된 게시글이 조회됨 - 테스트 실패!");
            throw new AssertionError("삭제된 게시글이 여전히 조회 가능합니다.");
        } catch (Exception e) {
            log.info("삭제된 게시글 조회 시 예외 발생 (정상): {}", e.getMessage());
        }

        log.info("게시글 삭제 테스트 성공!");
        log.info("=== 게시글 삭제 테스트 완료 ===");
    }

    @Test
    @DisplayName("PATCH /api/v1/class/{classId}/boards/{id}/restore - 게시글 복원 테스트")
    void restoreBoardTest() throws Exception {
        log.info("=== 게시글 복원 테스트 시작 ===");
        
        // 먼저 게시글을 삭제
        boardService.remove(testBoardId);
        log.info("게시글 {} 삭제 완료", testBoardId);

        // 삭제 확인
        try {
            boardService.readOne(testBoardId);
            throw new AssertionError("게시글이 삭제되지 않았습니다.");
        } catch (Exception e) {
            log.info("게시글 삭제 확인: {}", e.getMessage());
        }

        // When & Then - 복원
        MvcResult result = mockMvc.perform(patch("/api/v1/class/{classId}/boards/{id}/restore", testClassId, testBoardId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.result").value("success"))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        log.info("복원 응답: {}", responseContent);

        // 복원 후 조회 확인
        BoardDTO restoredBoard = boardService.readOne(testBoardId);
        assertThat(restoredBoard).isNotNull();
        assertThat(restoredBoard.getTitle()).isEqualTo("테스트 게시글");
        
        log.info("복원된 게시글 제목: {}", restoredBoard.getTitle());
        log.info("게시글 복원 테스트 성공!");
        log.info("=== 게시글 복원 테스트 완료 ===");
    }

    @Test
    @DisplayName("GET /api/v1/class/{classId}/boards - 게시글 목록 조회 테스트")
    void listBoardsTest() throws Exception {
        log.info("=== 게시글 목록 조회 테스트 시작 ===");

        // Given - 추가 테스트 데이터 생성
        List<Long> createdBoardIds = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            BoardDTO additionalBoard = BoardDTO.builder()
                    .title("추가 게시글 " + i)
                    .content("추가 게시글 내용 " + i)
                    .writer("tester" + i)
                    .classId(testClassId)
                    .categoryId(testCategoryId)
                    .boardType("NORMAL")
                    .build();
            Long boardId = boardService.register(additionalBoard);
            createdBoardIds.add(boardId);
        }
        log.info("추가 테스트 데이터 3개 생성 완료: {}", createdBoardIds);

        // 기대값 계산 - 기존 1개 + 추가 3개 = 총 4개
        int expectedTotalCount = 4;

        // When & Then
        MvcResult result = mockMvc.perform(get("/api/v1/class/{classId}/boards", testClassId)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.dtoList").isArray())
                .andExpect(jsonPath("$.dtoList").isNotEmpty())
                .andExpect(jsonPath("$.total").value(expectedTotalCount))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10)) // 요청한 페이지 사이즈
                .andExpect(jsonPath("$.dtoList.length()").value(expectedTotalCount))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        log.info("목록 조회 응답: {}", responseContent);

        // 응답 데이터 상세 검증
        @SuppressWarnings("unchecked")
        Map<String, Object> response = objectMapper.readValue(responseContent, Map.class);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> boardList = (List<Map<String, Object>>) response.get("dtoList");
        
        // 게시글 제목들이 올바르게 포함되어 있는지 확인
        List<String> titles = boardList.stream()
            .map(board -> (String) board.get("title"))
            .toList();
        
        assertThat(titles).contains("테스트 게시글", "추가 게시글 1", "추가 게시글 2", "추가 게시글 3");
        
        // 모든 게시글이 같은 클래스에 속하는지 확인
        List<Integer> classIds = boardList.stream()
            .map(board -> (Integer) board.get("classId"))
            .toList();
        
        assertThat(classIds).allMatch(id -> id.equals(testClassId.intValue()));

        log.info("게시글 목록 조회 테스트 성공! 총 {}개 게시글 조회됨", boardList.size());
        log.info("=== 게시글 목록 조회 테스트 완료 ===");
    }

    @Test
    @DisplayName("GET /api/v1/class/{classId}/boards - 페이징 테스트")
    void listBoardsPagingTest() throws Exception {
        log.info("=== 페이징 테스트 시작 ===");

        // Given - 페이징 테스트를 위한 충분한 데이터 생성 (100개로 증가)
        for (int i = 1; i <= 99; i++) {  // 기존 1개 + 99개 = 총 100개
            BoardDTO additionalBoard = BoardDTO.builder()
                .title("페이징 테스트 게시글 " + i)
                .content("페이징 테스트 내용 " + i)
                .writer("pagingTester" + i)
                .classId(testClassId)
                .categoryId(testCategoryId)
                .boardType("NORMAL")
                .build();
            boardService.register(additionalBoard);
        }

    // 첫 번째 페이지 조회 (사이즈 5) - 페이지 그룹 1~10, 실제 페이지 1~20
    MvcResult page1Result = mockMvc.perform(get("/api/v1/class/{classId}/boards", testClassId)
                    .param("page", "1")
                    .param("size", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(100))
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(5))
            .andExpect(jsonPath("$.dtoList.length()").value(5))
            .andExpect(jsonPath("$.start").value(1))  // 페이지 그룹 시작
            .andExpect(jsonPath("$.end").value(10))   // 페이지 그룹 끝 (실제 마지막 페이지 20보다 작음)
            .andExpect(jsonPath("$.next").value(true))   // 11~20 페이지 그룹 존재
            .andExpect(jsonPath("$.prev").value(false))  // 이전 페이지 그룹 없음
            .andReturn();

    // 10번째 페이지 조회 (첫 번째 페이지 그룹의 마지막)
    mockMvc.perform(get("/api/v1/class/{classId}/boards", testClassId)
                    .param("page", "10")
                    .param("size", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(10))
            .andExpect(jsonPath("$.start").value(1))
            .andExpect(jsonPath("$.end").value(10))
            .andExpect(jsonPath("$.next").value(true))   // 여전히 다음 그룹 존재 (11~20)
            .andExpect(jsonPath("$.prev").value(false));

    // 11번째 페이지 조회 (두 번째 페이지 그룹의 시작)
    mockMvc.perform(get("/api/v1/class/{classId}/boards", testClassId)
                    .param("page", "11") 
                    .param("size", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(11))
            .andExpect(jsonPath("$.start").value(11))  // 두 번째 그룹 시작
            .andExpect(jsonPath("$.end").value(20))    // 두 번째 그룹 끝
            .andExpect(jsonPath("$.next").value(false)) // 더 이상 다음 그룹 없음 (100개 = 20페이지가 마지막)
            .andExpect(jsonPath("$.prev").value(true)); // 이전 그룹 존재 (1~10)

        log.info("페이지네이션 UI 로직 테스트 성공!");
        log.info("=== 페이징 테스트 완료 ===");
    }

    @Test
    @DisplayName("GET /api/v1/class/{classId}/boards - 검색 기능 테스트")
    void listBoardsSearchTest() throws Exception {
        log.info("=== 검색 기능 테스트 시작 ===");

        // Given - 검색 테스트용 데이터 생성
        boardService.register(BoardDTO.builder()
            .title("Java 프로그래밍")
            .content("Java 기초 학습")
            .writer("javaTeacher")
            .classId(testClassId)
            .categoryId(testCategoryId)
            .boardType("NORMAL")
            .build());

        boardService.register(BoardDTO.builder()
            .title("Spring Boot 실습")
            .content("Spring Boot 심화")
            .writer("springTeacher")
            .classId(testClassId)
            .categoryId(testCategoryId)
            .boardType("NORMAL")
            .build());

        // 제목으로 검색
        MvcResult titleSearchResult = mockMvc.perform(get("/api/v1/class/{classId}/boards", testClassId)
                        .param("page", "1")
                        .param("size", "10")
                        .param("type", "t")
                        .param("keyword", "Java"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dtoList").isArray())
            .andExpect(jsonPath("$.dtoList[?(@.title =~ /.*Java.*/)]").exists())
            .andReturn();

        // 작성자로 검색
        mockMvc.perform(get("/api/v1/class/{classId}/boards", testClassId)
                        .param("page", "1")
                        .param("size", "10")
                        .param("type", "w")
                        .param("keyword", "springTeacher"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dtoList").isArray())
            .andExpect(jsonPath("$.dtoList[?(@.writer == 'springTeacher')]").exists());

        log.info("검색 기능 테스트 성공!");
        log.info("=== 검색 기능 테스트 완료 ===");
    }

    @Test
    @DisplayName("전체 엔드포인트 플로우 테스트")
    void fullWorkflowTest() throws Exception {
        log.info("=== 전체 워크플로우 테스트 시작 ===");

        // 1. 게시글 등록
        BoardDTO newBoard = BoardDTO.builder()
                .title("워크플로우 테스트 게시글")
                .content("워크플로우 테스트 내용")
                .writer("workflowTester")
                .categoryId(testCategoryId)
                .boardType("NORMAL")
                .build();

        String registerJson = objectMapper.writeValueAsString(newBoard);
        MvcResult registerResult = mockMvc.perform(post("/api/v1/class/{classId}/boards", testClassId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated())
                .andReturn();

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> registerResponse = objectMapper.readValue(
                registerResult.getResponse().getContentAsString(), java.util.Map.class);
        Long workflowBoardId = ((Integer) registerResponse.get("id")).longValue();
        log.info("1. 게시글 등록 완료 - ID: {}", workflowBoardId);

        // 2. 게시글 조회
        mockMvc.perform(get("/api/v1/class/{classId}/boards/{id}", testClassId, workflowBoardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("워크플로우 테스트 게시글"));
        log.info("2. 게시글 조회 완료");

        // 3. 좋아요 추가
        mockMvc.perform(post("/api/v1/class/{classId}/boards/{id}/favorite", testClassId, workflowBoardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favoriteCount").value(1));
        log.info("3. 좋아요 추가 완료");

        // 4. 게시글 수정
        newBoard.setId(workflowBoardId);
        newBoard.setTitle("수정된 워크플로우 게시글");
        String modifyJson = objectMapper.writeValueAsString(newBoard);
        mockMvc.perform(put("/api/v1/class/{classId}/boards/{id}", testClassId, workflowBoardId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modifyJson))
                .andExpect(status().isOk());
        log.info("4. 게시글 수정 완료");

        // 5. 게시글 삭제
        mockMvc.perform(delete("/api/v1/class/{classId}/boards/{id}", testClassId, workflowBoardId))
                .andExpect(status().isNoContent());
        log.info("5. 게시글 삭제 완료");

        // 6. 게시글 복원
        mockMvc.perform(patch("/api/v1/class/{classId}/boards/{id}/restore", testClassId, workflowBoardId))
                .andExpect(status().isOk());
        log.info("6. 게시글 복원 완료");

        log.info("=== 전체 워크플로우 테스트 성공! ===");
    }
}