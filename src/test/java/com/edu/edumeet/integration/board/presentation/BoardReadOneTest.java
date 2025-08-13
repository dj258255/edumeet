package com.edu.edumeet.integration.board.presentation;


import com.edu.edumeet.attachment.presentation.dto.AttachmentDTO;
import com.edu.edumeet.board.domain.BoardCategory;
import com.edu.edumeet.board.infrastructure.BoardCategoryJpaEntity;
import com.edu.edumeet.board.infrastructure.BoardCategoryJpaRepository;
import com.edu.edumeet.board.infrastructure.BoardJpaRepository;
import com.edu.edumeet.board.presentation.BoardService;
import com.edu.edumeet.board.presentation.dto.BoardDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Log4j2
@Transactional
public class BoardReadOneTest {

    @Autowired
    private BoardService boardService;

    @Autowired
    private BoardJpaRepository boardJpaRepository;

    @Autowired
    private BoardCategoryJpaRepository boardCategoryJpaRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Long testBoardId;
    private Long testCategoryId;

    @BeforeEach
    void setUp() {
        // 테스트 카테고리 생성
        BoardCategory category = BoardCategory.builder()
                .categoryName("테스트 카테고리")
                .classId(1L)
                .createdBy("tester")
                .build();
        
        BoardCategoryJpaEntity savedCategory = boardCategoryJpaRepository.save(BoardCategoryJpaEntity.fromDomain(category));
        testCategoryId = savedCategory.getId();
        
        // 테스트 게시글 생성
        BoardDTO boardDTO = BoardDTO.builder()
                .title("테스트 게시글")
                .content("테스트 내용")
                .writer("tester")
                .classId(1L)
                .categoryId(testCategoryId)
                .build();
        
        testBoardId = boardService.register(boardDTO);
        
        // 이미지 추가
        String uuid = UUID.randomUUID().toString();
        String fileName = "test_image.jpg";
        boardService.addImageToBoard(testBoardId, uuid, fileName);
    }

    @Test
    @DisplayName("게시글 조회 응답 형식 테스트")
    void readOneResponseFormatTest() {
        // when
        BoardDTO boardDTO = boardService.readOne(testBoardId);
        
        // then
        assertThat(boardDTO).isNotNull();
        assertThat(boardDTO.getId()).isEqualTo(testBoardId);
        
        // 이미지 확인
        List<AttachmentDTO> boardImages = boardDTO.getBoardImages();
        assertThat(boardImages).isNotNull();
        assertThat(boardImages).isNotEmpty();
        
        // 이미지 정보 출력
        log.info("Board Images: {}", boardImages);
        
        // 각 이미지 필드 확인
        AttachmentDTO imageDTO = boardImages.get(0);
        assertThat(imageDTO.getUuid()).isNotNull();
        assertThat(imageDTO.getFileName()).isNotNull();
        assertThat(imageDTO.getS3Url()).isNotNull();
        assertThat(imageDTO.getS3ThumbnailUrl()).isNotNull();
        
        // 전체 DTO를 JSON으로 변환하여 출력
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(boardDTO);
            log.info("BoardDTO as JSON:\n{}", json);
        } catch (Exception e) {
            log.error("JSON 변환 실패", e);
        }
    }
}