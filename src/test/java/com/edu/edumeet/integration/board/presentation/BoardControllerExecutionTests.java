package com.edu.edumeet.integration.board.presentation;

import com.edu.edumeet.board.controller.BoardController;
import com.edu.edumeet.board.service.BoardService;
import com.edu.edumeet.board.dto.BoardListAllDTO;
import com.edu.edumeet.board.dto.PageRequestDTO;
import com.edu.edumeet.board.dto.PageResponseDTO;
import com.edu.edumeet.config.jwt.JwtService;
import com.edu.edumeet.s3.util.S3Uploader;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 게시판 컨트롤러 단위 테스트 클래스
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("게시판 컨트롤러 실행 테스트")
public class BoardControllerExecutionTests {
    @Mock
    private BoardService boardService;

    @Mock
    private S3Uploader s3Uploader;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private BoardController boardController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(boardController).build();
        objectMapper = new ObjectMapper();
    }
    
    @Test
    @DisplayName("클래스별 게시글 목록 조회 테스트")
    void listTest() throws Exception {
        // given
        Long classId = 1L;
        
        BoardListAllDTO dto1 = BoardListAllDTO.builder()
                .id(1L)
                .title("게시글 1")
                .writer("user1")
                .regDate(LocalDateTime.now())
                .replyCount(5L)
                .classId(classId)
                .view(10)
                .favorite(5)
                .dislike(5)
                .build();
        
        BoardListAllDTO dto2 = BoardListAllDTO.builder()
                .id(2L)
                .title("게시글 2")
                .writer("user2")
                .regDate(LocalDateTime.now())
                .replyCount(3L)
                .classId(classId)
                .view(20)
                .favorite(15)
                .dislike(5)
                .build();
        
        PageResponseDTO<BoardListAllDTO> responseDTO = PageResponseDTO.<BoardListAllDTO>withAll()
                .pageRequestDTO(new PageRequestDTO())
                .dtoList(List.of(dto1, dto2))
                .total(2)
                .build();
        
        when(boardService.listWithAll(any(PageRequestDTO.class))).thenReturn(responseDTO);
        
        // when & then
        mockMvc.perform(get("/api/v1/class/{classId}/boards", classId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dtoList").isArray())
                .andExpect(jsonPath("$.dtoList.length()").value(2))
                .andExpect(jsonPath("$.dtoList[0].id").value(1))
                .andExpect(jsonPath("$.dtoList[0].title").value("게시글 1"))
                .andExpect(jsonPath("$.dtoList[1].id").value(2))
                .andExpect(jsonPath("$.dtoList[1].title").value("게시글 2"))
                .andExpect(jsonPath("$.total").value(2));
        
        // ArgumentCaptor를 사용하여 더 정확한 검증
        ArgumentCaptor<PageRequestDTO> captor = ArgumentCaptor.forClass(PageRequestDTO.class);
        verify(boardService, times(1)).listWithAll(captor.capture());
        
        PageRequestDTO capturedRequest = captor.getValue();
        assertThat(capturedRequest.getClassId()).isEqualTo(classId);
        assertThat(capturedRequest.getCategoryId()).isNull();
        assertThat(capturedRequest.getBoardType()).isNull();
    }
    
    @Test
    @DisplayName("카테고리별 게시글 목록 조회 테스트")
    void listByCategoryTest() throws Exception {
        // given
        Long classId = 1L;
        Long categoryId = 10L;
        
        BoardListAllDTO dto1 = BoardListAllDTO.builder()
                .id(1L)
                .title("카테고리 게시글 1")
                .writer("user1")
                .regDate(LocalDateTime.now())
                .replyCount(5L)
                .classId(classId)
                .categoryId(categoryId)
                .view(10)
                .favorite(5)
                .build();
        
        PageResponseDTO<BoardListAllDTO> responseDTO = PageResponseDTO.<BoardListAllDTO>withAll()
                .pageRequestDTO(new PageRequestDTO())
                .dtoList(List.of(dto1))
                .total(1)
                .build();
        
        when(boardService.listWithAll(any(PageRequestDTO.class))).thenReturn(responseDTO);
        
        // when & then
        mockMvc.perform(get("/api/v1/class/{classId}/boards", classId)
                .param("categoryId", String.valueOf(categoryId))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dtoList").isArray())
                .andExpect(jsonPath("$.dtoList.length()").value(1))
                .andExpect(jsonPath("$.dtoList[0].id").value(1))
                .andExpect(jsonPath("$.dtoList[0].title").value("카테고리 게시글 1"))
                .andExpect(jsonPath("$.total").value(1));
        
        // ArgumentCaptor를 사용하여 파라미터 검증
        ArgumentCaptor<PageRequestDTO> captor = ArgumentCaptor.forClass(PageRequestDTO.class);
        verify(boardService, times(1)).listWithAll(captor.capture());
        
        PageRequestDTO capturedRequest = captor.getValue();
        assertThat(capturedRequest.getClassId()).isEqualTo(classId);
        assertThat(capturedRequest.getCategoryId()).isEqualTo(categoryId);
        assertThat(capturedRequest.getBoardType()).isNull();
    }
    
    @Test
    @DisplayName("게시글 타입별 목록 조회 테스트")
    void listByBoardTypeTest() throws Exception {
        // given
        Long classId = 1L;
        String boardType = "NOTICE";
        
        BoardListAllDTO dto1 = BoardListAllDTO.builder()
                .id(1L)
                .title("공지사항 1")
                .writer("admin")
                .regDate(LocalDateTime.now())
                .replyCount(0L)
                .classId(classId)
                .view(50)
                .favorite(20)
                .build();
        
        PageResponseDTO<BoardListAllDTO> responseDTO = PageResponseDTO.<BoardListAllDTO>withAll()
                .pageRequestDTO(PageRequestDTO.builder().page(1).size(10).classId(classId).boardType(boardType).build())
                .dtoList(List.of(dto1))
                .total(1)
                .build();
        
        // any()를 사용하여 모든 PageRequestDTO에 대해 동일한 응답 반환
        when(boardService.listWithAll(any(PageRequestDTO.class))).thenReturn(responseDTO);
        
        // when & then
        mockMvc.perform(get("/api/v1/class/{classId}/boards", classId)
                .param("boardType", boardType)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dtoList").isArray())
                .andExpect(jsonPath("$.dtoList.length()").value(1))
                .andExpect(jsonPath("$.dtoList[0].id").value(1))
                .andExpect(jsonPath("$.dtoList[0].title").value("공지사항 1"))
                .andExpect(jsonPath("$.total").value(1));
        
        // ArgumentCaptor를 사용하여 실제 호출된 파라미터 검증
        ArgumentCaptor<PageRequestDTO> captor = ArgumentCaptor.forClass(PageRequestDTO.class);
        verify(boardService, times(1)).listWithAll(captor.capture());
        
        PageRequestDTO capturedRequest = captor.getValue();
        assertThat(capturedRequest.getClassId()).isEqualTo(classId);
        assertThat(capturedRequest.getBoardType()).isEqualTo(boardType);
    }
    
    @Test
    @DisplayName("카테고리와 게시글 타입을 모두 적용한 목록 조회 테스트")
    void listByCategoryAndBoardTypeTest() throws Exception {
        // given
        Long classId = 1L;
        Long categoryId = 10L;
        String boardType = "RECOMMENDED";
        
        BoardListAllDTO dto1 = BoardListAllDTO.builder()
                .id(1L)
                .title("추천 게시글 1")
                .writer("user1")
                .regDate(LocalDateTime.now())
                .replyCount(10L)
                .classId(classId)
                .categoryId(categoryId)
                .view(100)
                .favorite(30)
                .build();
        
        PageResponseDTO<BoardListAllDTO> responseDTO = PageResponseDTO.<BoardListAllDTO>withAll()
                .pageRequestDTO(new PageRequestDTO())
                .dtoList(List.of(dto1))
                .total(1)
                .build();
        
        when(boardService.listWithAll(any(PageRequestDTO.class))).thenReturn(responseDTO);
        
        // when & then
        mockMvc.perform(get("/api/v1/class/{classId}/boards", classId)
                .param("categoryId", String.valueOf(categoryId))
                .param("boardType", boardType)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dtoList").isArray())
                .andExpect(jsonPath("$.dtoList.length()").value(1))
                .andExpect(jsonPath("$.dtoList[0].id").value(1))
                .andExpect(jsonPath("$.dtoList[0].title").value("추천 게시글 1"))
                .andExpect(jsonPath("$.total").value(1));
        
        // 모든 파라미터가 제대로 전달되었는지 검증
        ArgumentCaptor<PageRequestDTO> captor = ArgumentCaptor.forClass(PageRequestDTO.class);
        verify(boardService, times(1)).listWithAll(captor.capture());
        
        PageRequestDTO capturedRequest = captor.getValue();
        assertThat(capturedRequest.getClassId()).isEqualTo(classId);
        assertThat(capturedRequest.getCategoryId()).isEqualTo(categoryId);
        assertThat(capturedRequest.getBoardType()).isEqualTo(boardType);
    }
    
    @Test
    @DisplayName("페이징 파라미터 테스트")
    void listWithPagingTest() throws Exception {
        // given
        Long classId = 1L;
        int page = 2;
        int size = 5;
        
        BoardListAllDTO dto1 = BoardListAllDTO.builder()
                .id(1L)
                .title("페이징 게시글")
                .writer("user1")
                .regDate(LocalDateTime.now())
                .replyCount(1L)
                .classId(classId)
                .view(10)
                .favorite(5)
                .build();
        
        PageResponseDTO<BoardListAllDTO> responseDTO = PageResponseDTO.<BoardListAllDTO>withAll()
                .pageRequestDTO(PageRequestDTO.builder().page(page).size(size).classId(classId).build())
                .dtoList(List.of(dto1))
                .total(1)
                .build();
        
        // Mock을 더 구체적으로 설정 - ArgumentMatcher 사용
        when(boardService.listWithAll(argThat(req -> 
            req.getClassId().equals(classId) && 
            req.getPage() == page && 
            req.getSize() == size
        ))).thenReturn(responseDTO);
        
        // when & then
        mockMvc.perform(get("/api/v1/class/{classId}/boards", classId)
                .param("page", String.valueOf(page))
                .param("size", String.valueOf(size))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dtoList").isArray())
                .andExpect(jsonPath("$.total").value(1));
        
        // 페이징 파라미터 검증
        ArgumentCaptor<PageRequestDTO> captor = ArgumentCaptor.forClass(PageRequestDTO.class);
        verify(boardService, times(1)).listWithAll(captor.capture());
        
        PageRequestDTO capturedRequest = captor.getValue();
        assertThat(capturedRequest.getClassId()).isEqualTo(classId);
        assertThat(capturedRequest.getPage()).isEqualTo(page);
        assertThat(capturedRequest.getSize()).isEqualTo(size);
    }
    
    @Test
    @DisplayName("검색 조건 테스트")
    void listWithSearchTest() throws Exception {
        // given
        Long classId = 1L;
        String keyword = "검색어";
        String type = "t"; // title 검색
        
        BoardListAllDTO dto1 = BoardListAllDTO.builder()
                .id(1L)
                .title("검색어가 포함된 제목")
                .writer("user1")
                .regDate(LocalDateTime.now())
                .replyCount(1L)
                .classId(classId)
                .view(10)
                .favorite(5)
                .build();
        
        PageResponseDTO<BoardListAllDTO> responseDTO = PageResponseDTO.<BoardListAllDTO>withAll()
                .pageRequestDTO(new PageRequestDTO())
                .dtoList(List.of(dto1))
                .total(1)
                .build();
        
        when(boardService.listWithAll(any(PageRequestDTO.class))).thenReturn(responseDTO);
        
        // when & then
        mockMvc.perform(get("/api/v1/class/{classId}/boards", classId)
                .param("keyword", keyword)
                .param("type", type)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dtoList").isArray())
                .andExpect(jsonPath("$.total").value(1));
        
        // 검색 조건 검증
        ArgumentCaptor<PageRequestDTO> captor = ArgumentCaptor.forClass(PageRequestDTO.class);
        verify(boardService, times(1)).listWithAll(captor.capture());
        
        PageRequestDTO capturedRequest = captor.getValue();
        assertThat(capturedRequest.getClassId()).isEqualTo(classId);
        assertThat(capturedRequest.getKeyword()).isEqualTo(keyword);
        assertThat(capturedRequest.getType()).isEqualTo(type);
    }
}