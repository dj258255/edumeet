package com.edu.edumeet.unit.board;

import com.edu.edumeet.board.repository.BoardCategoryRepository;
import com.edu.edumeet.board.service.BoardCategoryService;
import com.edu.edumeet.board.domain.BoardCategory;
import com.edu.edumeet.board.dto.BoardCategoryDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BoardCategoryServiceImplTest {

    @Mock
    private BoardCategoryRepository boardCategoryRepository;

    @InjectMocks
    private BoardCategoryService boardCategoryService;

    private BoardCategory rootCategory;
    private BoardCategory subCategory;
    private BoardCategoryDTO rootCategoryDTO;
    private BoardCategoryDTO subCategoryDTO;

    @BeforeEach
    void setUp() {
        // 테스트용 도메인 객체 생성
        rootCategory = BoardCategory.builder()
                .id(1L)
                .categoryName("루트 카테고리")
                .description("루트 카테고리 설명")
                .classId(1L)
                .createdBy("admin")
                .parentId(null)
                .isActive(true)
                .sortOrder(1)
                .recommendThreshold(10)
                .regDate(LocalDateTime.now())
                .build();

        subCategory = BoardCategory.builder()
                .id(2L)
                .categoryName("하위 카테고리")
                .description("하위 카테고리 설명")
                .classId(1L)
                .createdBy("admin")
                .parentId(1L)
                .isActive(true)
                .sortOrder(1)
                .recommendThreshold(15)
                .regDate(LocalDateTime.now())
                .build();

        // 테스트용 DTO 객체 생성
        rootCategoryDTO = BoardCategoryDTO.builder()
                .id(1L)
                .categoryName("루트 카테고리")
                .description("루트 카테고리 설명")
                .classId(1L)
                .createdBy("admin")
                .parentId(null)
                .isActive(true)
                .sortOrder(1)
                .recommendThreshold(10)
                .regDate(LocalDateTime.now())
                .build();

        subCategoryDTO = BoardCategoryDTO.builder()
                .id(2L)
                .categoryName("하위 카테고리")
                .description("하위 카테고리 설명")
                .classId(1L)
                .createdBy("admin")
                .parentId(1L)
                .isActive(true)
                .sortOrder(1)
                .recommendThreshold(15)
                .regDate(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("카테고리 등록 테스트")
    void registerTest() {
        // given
        when(boardCategoryRepository.save(any(BoardCategory.class))).thenReturn(1L);

        // when
        Long categoryId = boardCategoryService.register(rootCategoryDTO);

        // then
        assertThat(categoryId).isEqualTo(1L);
        verify(boardCategoryRepository, times(1)).save(any(BoardCategory.class));
    }

    @Test
    @DisplayName("카테고리 조회 테스트")
    void readOneTest() {
        // given
        when(boardCategoryRepository.findById(1L)).thenReturn(Optional.of(rootCategory));

        // when
        BoardCategoryDTO result = boardCategoryService.readOne(1L);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(rootCategory.getId());
        assertThat(result.getCategoryName()).isEqualTo(rootCategory.getCategoryName());
        verify(boardCategoryRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("존재하지 않는 카테고리 조회 시 예외 발생 테스트")
    void readOneNotFoundTest() {
        // given
        when(boardCategoryRepository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> boardCategoryService.readOne(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("카테고리를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("카테고리 수정 테스트")
    void modifyTest() {
        // given
        BoardCategoryDTO updateDTO = BoardCategoryDTO.builder()
                .id(1L)
                .categoryName("수정된 카테고리")
                .description("수정된 설명")
                .classId(1L)
                .createdBy("admin")
                .parentId(null)
                .isActive(true)
                .sortOrder(1)
                .recommendThreshold(20)
                .build();

        when(boardCategoryRepository.findById(1L)).thenReturn(Optional.of(rootCategory));

        // when
        boardCategoryService.modify(updateDTO);

        // then
        verify(boardCategoryRepository, times(1)).findById(1L);
        verify(boardCategoryRepository, times(1)).save(any(BoardCategory.class));
    }

    @Test
    @DisplayName("하위 카테고리가 없는 경우 카테고리 삭제 성공")
    void removeSuccessWhenNoSubCategories() {
        // given
        when(boardCategoryRepository.findByParentId(1L)).thenReturn(List.of());

        // when
        boardCategoryService.remove(1L);

        // then
        verify(boardCategoryRepository).findByParentId(1L);
        verify(boardCategoryRepository).deleteById(1L);
    }


    @Test
    @DisplayName("하위 카테고리가 있는 카테고리 삭제 시 예외 발생 테스트")
    void removeWithSubCategoriesTest() {
        // given
        when(boardCategoryRepository.findByParentId(1L)).thenReturn(List.of(subCategory));

        // when & then
        assertThatThrownBy(() -> boardCategoryService.remove(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("하위 카테고리가 있는 카테고리는 삭제할 수 없습니다");
    }

    @Test
    @DisplayName("클래스별 카테고리 목록 조회 테스트")
    void getListByClassIdTest() {
        // given
        when(boardCategoryRepository.findByClassId(1L)).thenReturn(Arrays.asList(rootCategory, subCategory));

        // when
        List<BoardCategoryDTO> result = boardCategoryService.getListByClassId(1L);

        // then
        assertThat(result).hasSize(2);
        verify(boardCategoryRepository, times(1)).findByClassId(1L);
    }

    @Test
    @DisplayName("루트 카테고리 목록 조회 테스트")
    void getRootCategoriesTest() {
        // given
        when(boardCategoryRepository.findRootCategories(1L)).thenReturn(List.of(rootCategory));

        // when
        List<BoardCategoryDTO> result = boardCategoryService.getRootCategories(1L);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).isRootCategory()).isTrue();
        verify(boardCategoryRepository, times(1)).findRootCategories(1L);
    }

    @Test
    @DisplayName("하위 카테고리 목록 조회 테스트")
    void getSubCategoriesTest() {
        // given
        when(boardCategoryRepository.findByParentId(1L)).thenReturn(List.of(subCategory));

        // when
        List<BoardCategoryDTO> result = boardCategoryService.getSubCategories(1L);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getParentId()).isEqualTo(1L);
        verify(boardCategoryRepository, times(1)).findByParentId(1L);
    }

    @Test
    @DisplayName("카테고리 이동 테스트")
    void moveCategoryTest() {
        // given
        Long newParentId = 3L;
        BoardCategory newParentCategory = BoardCategory.builder()
                .id(3L)
                .categoryName("새 부모 카테고리")
                .classId(1L)
                .build();

        when(boardCategoryRepository.findById(2L)).thenReturn(Optional.of(subCategory));
        when(boardCategoryRepository.findById(3L)).thenReturn(Optional.of(newParentCategory));
        when(boardCategoryRepository.findByParentId(2L)).thenReturn(List.of());

        // when
        boardCategoryService.moveCategory(2L, newParentId);

        // then
        verify(boardCategoryRepository, times(1)).findById(2L);
        verify(boardCategoryRepository, times(1)).findById(3L);
        verify(boardCategoryRepository, times(1)).findByParentId(2L);
        verify(boardCategoryRepository, times(1)).save(any(BoardCategory.class));
    }

    @Test
    @DisplayName("카테고리 활성화/비활성화 테스트")
    void setActiveTest() {
        // given
        when(boardCategoryRepository.findById(1L)).thenReturn(Optional.of(rootCategory));

        // when
        boardCategoryService.setActive(1L, false);

        // then
        verify(boardCategoryRepository, times(1)).findById(1L);
        verify(boardCategoryRepository, times(1)).save(any(BoardCategory.class));
    }

    @Test
    @DisplayName("카테고리 추천 게시글 기준값 변경 테스트")
    void changeRecommendThresholdTest() {
        // given
        when(boardCategoryRepository.findById(1L)).thenReturn(Optional.of(rootCategory));

        // when
        boardCategoryService.changeRecommendThreshold(1L, 20);

        // then
        verify(boardCategoryRepository, times(1)).findById(1L);
        verify(boardCategoryRepository, times(1)).save(any(BoardCategory.class));
    }
}