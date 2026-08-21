package com.edu.edumeet.board.controller;

import com.edu.edumeet.board.service.BoardCategoryService;
import com.edu.edumeet.board.dto.BoardCategoryDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 게시판 카테고리 컨트롤러
 * 카테고리 관리 API를 제공
 */
@RestController
@RequestMapping("/api/classes/{classId}/categories")
@Log4j2
@RequiredArgsConstructor
@Tag(name = "게시판 카테고리 API", description = "게시판 카테고리 관리 API")
public class BoardCategoryController {

    private final BoardCategoryService boardCategoryService;

    /**
     * 카테고리 목록 조회
     * @param classId 클래스 ID
     * @return 카테고리 목록
     */
    @Operation(summary = "카테고리 목록 조회", description = "클래스별 카테고리 목록을 조회합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping
    public ResponseEntity<List<BoardCategoryDTO>> getList(
            @Parameter(description = "클래스 ID", required = true)
            @PathVariable("classId") Long classId) {
        
        log.info("카테고리 목록 조회 -> 클래스 ID: {}", classId);
        
        List<BoardCategoryDTO> categoryList = boardCategoryService.getListByClassId(classId);
        
        return ResponseEntity.ok(categoryList);
    }

    /**
     * 루트 카테고리 목록 조회
     * @param classId 클래스 ID
     * @return 루트 카테고리 목록
     */
    @Operation(summary = "루트 카테고리 목록 조회", description = "클래스별 루트 카테고리 목록을 조회합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/roots")
    public ResponseEntity<List<BoardCategoryDTO>> getRootCategories(
            @Parameter(description = "클래스 ID", required = true)
            @PathVariable("classId") Long classId) {
        
        log.info("루트 카테고리 목록 조회 -> 클래스 ID: {}", classId);
        
        List<BoardCategoryDTO> rootCategories = boardCategoryService.getRootCategories(classId);
        
        return ResponseEntity.ok(rootCategories);
    }

    /**
     * 하위 카테고리 목록 조회
     * @param classId 클래스 ID
     * @param parentId 부모 카테고리 ID
     * @return 하위 카테고리 목록
     */
    @Operation(summary = "하위 카테고리 목록 조회", description = "특정 카테고리의 하위 카테고리 목록을 조회합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/{parentId}/subcategories")
    public ResponseEntity<List<BoardCategoryDTO>> getSubCategories(
            @Parameter(description = "클래스 ID", required = true)
            @PathVariable("classId") Long classId,
            @Parameter(description = "부모 카테고리 ID", required = true)
            @PathVariable("parentId") Long parentId) {
        
        log.info("하위 카테고리 목록 조회 -> 클래스 ID: {}, 부모 카테고리 ID: {}", classId, parentId);
        
        List<BoardCategoryDTO> subCategories = boardCategoryService.getSubCategories(parentId);
        
        return ResponseEntity.ok(subCategories);
    }

    /**
     * 카테고리 상세 조회
     * @param classId 클래스 ID
     * @param id 카테고리 ID
     * @return 카테고리 정보
     */
    @Operation(summary = "카테고리 상세 조회", description = "특정 카테고리의 상세 정보를 조회합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "카테고리를 찾을 수 없음"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/{id}")
    public ResponseEntity<BoardCategoryDTO> read(
            @Parameter(description = "클래스 ID", required = true)
            @PathVariable("classId") Long classId,
            @Parameter(description = "카테고리 ID", required = true)
            @PathVariable("id") Long id) {
        
        log.info("카테고리 상세 조회 -> 클래스 ID: {}, 카테고리 ID: {}", classId, id);
        
        BoardCategoryDTO categoryDTO = boardCategoryService.readOne(id);
        
        // 클래스 ID 검증
        if (!classId.equals(categoryDTO.getClassId())) {
            log.warn("권한 없는 접근 시도 - 요청 클래스 ID: {}, 실제 카테고리 클래스 ID: {}", 
                    classId, categoryDTO.getClassId());
            throw new IllegalArgumentException("해당 클래스의 카테고리가 아닙니다.");
        }
        
        return ResponseEntity.ok(categoryDTO);
    }

    /**
     * 카테고리 등록
     * @param classId 클래스 ID
     * @param categoryDTO 등록할 카테고리 정보
     * @param bindingResult 유효성 검사 결과
     * @return 등록된 카테고리 ID
     * @throws BindException 유효성 검사 실패 시
     */
    @Operation(summary = "카테고리 등록", description = "새로운 카테고리를 등록합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "등록 성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping
    public ResponseEntity<Map<String, Long>> register(
            @Parameter(description = "클래스 ID", required = true)
            @PathVariable("classId") Long classId,
            @Parameter(description = "등록할 카테고리 정보", required = true)
            @Valid @RequestBody BoardCategoryDTO categoryDTO,
            BindingResult bindingResult) throws BindException {
        
        log.info("카테고리 등록 -> 클래스 ID: {}, 카테고리: {}", classId, categoryDTO);
        
        if (bindingResult.hasErrors()) {
            throw new BindException(bindingResult);
        }
        
        // URL 경로의 클래스 ID 설정
        categoryDTO.setClassId(classId);
        
        Long categoryId = boardCategoryService.register(categoryDTO);
        
        Map<String, Long> resultMap = new HashMap<>();
        resultMap.put("id", categoryId);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(resultMap);
    }

    /**
     * 카테고리 수정
     * @param classId 클래스 ID
     * @param id 수정할 카테고리 ID
     * @param categoryDTO 수정할 카테고리 정보
     * @param bindingResult 유효성 검사 결과
     * @return 수정 결과
     * @throws BindException 유효성 검사 실패 시
     */
    @Operation(summary = "카테고리 수정", description = "기존 카테고리 정보를 수정합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "수정 성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "404", description = "카테고리를 찾을 수 없음"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, String>> modify(
            @Parameter(description = "클래스 ID", required = true)
            @PathVariable("classId") Long classId,
            @Parameter(description = "수정할 카테고리 ID", required = true)
            @PathVariable("id") Long id,
            @Parameter(description = "수정할 카테고리 정보", required = true)
            @Valid @RequestBody BoardCategoryDTO categoryDTO,
            BindingResult bindingResult) throws BindException {
        
        log.info("카테고리 수정 -> 클래스 ID: {}, 카테고리 ID: {}, 카테고리: {}", classId, id, categoryDTO);
        
        if (bindingResult.hasErrors()) {
            throw new BindException(bindingResult);
        }
        
        // 카테고리 ID 설정
        categoryDTO.setId(id);
        categoryDTO.setClassId(classId);
        
        // 기존 카테고리 조회 및 클래스 ID 검증
        BoardCategoryDTO existingCategory = boardCategoryService.readOne(id);
        if (!classId.equals(existingCategory.getClassId())) {
            log.warn("권한 없는 접근 시도 - 요청 클래스 ID: {}, 실제 카테고리 클래스 ID: {}", 
                    classId, existingCategory.getClassId());
            throw new IllegalArgumentException("해당 클래스의 카테고리가 아닙니다.");
        }
        
        boardCategoryService.modify(categoryDTO);
        
        Map<String, String> resultMap = new HashMap<>();
        resultMap.put("result", "success");
        
        return ResponseEntity.ok(resultMap);
    }

    /**
     * 카테고리 삭제
     * @param classId 클래스 ID
     * @param id 삭제할 카테고리 ID
     * @return 삭제 결과
     */
    @Operation(summary = "카테고리 삭제", description = "카테고리를 삭제합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "삭제 성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "404", description = "카테고리를 찾을 수 없음"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(
            @Parameter(description = "클래스 ID", required = true)
            @PathVariable("classId") Long classId,
            @Parameter(description = "삭제할 카테고리 ID", required = true)
            @PathVariable("id") Long id) {
        
        log.info("카테고리 삭제 -> 클래스 ID: {}, 카테고리 ID: {}", classId, id);
        
        // 기존 카테고리 조회 및 클래스 ID 검증
        BoardCategoryDTO existingCategory = boardCategoryService.readOne(id);
        if (!classId.equals(existingCategory.getClassId())) {
            log.warn("권한 없는 접근 시도 - 요청 클래스 ID: {}, 실제 카테고리 클래스 ID: {}", 
                    classId, existingCategory.getClassId());
            throw new IllegalArgumentException("해당 클래스의 카테고리가 아닙니다.");
        }
        
        boardCategoryService.remove(id);
        
        return ResponseEntity.noContent().build();
    }

    /**
     * 카테고리 이동 (부모 카테고리 변경)
     * @param classId 클래스 ID
     * @param id 이동할 카테고리 ID
     * @param newParentId 새 부모 카테고리 ID
     * @return 이동 결과
     */
    @Operation(summary = "카테고리 이동", description = "카테고리의 부모 카테고리를 변경합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "이동 성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "404", description = "카테고리를 찾을 수 없음"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PatchMapping("/{id}/move")
    public ResponseEntity<Map<String, String>> moveCategory(
            @Parameter(description = "클래스 ID", required = true)
            @PathVariable("classId") Long classId,
            @Parameter(description = "이동할 카테고리 ID", required = true)
            @PathVariable("id") Long id,
            @Parameter(description = "새 부모 카테고리 ID (null이면 루트 카테고리로 이동)", required = true)
            @RequestParam(required = false) Long newParentId) {
        
        log.info("카테고리 이동 -> 클래스 ID: {}, 카테고리 ID: {}, 새 부모 카테고리 ID: {}", 
                classId, id, newParentId);
        
        // 기존 카테고리 조회 및 클래스 ID 검증
        BoardCategoryDTO existingCategory = boardCategoryService.readOne(id);
        if (!classId.equals(existingCategory.getClassId())) {
            log.warn("권한 없는 접근 시도 - 요청 클래스 ID: {}, 실제 카테고리 클래스 ID: {}", 
                    classId, existingCategory.getClassId());
            throw new IllegalArgumentException("해당 클래스의 카테고리가 아닙니다.");
        }
        
        // 새 부모 카테고리가 있는 경우 클래스 ID 검증
        if (newParentId != null) {
            BoardCategoryDTO newParentCategory = boardCategoryService.readOne(newParentId);
            if (!classId.equals(newParentCategory.getClassId())) {
                log.warn("권한 없는 접근 시도 - 요청 클래스 ID: {}, 새 부모 카테고리 클래스 ID: {}", 
                        classId, newParentCategory.getClassId());
                throw new IllegalArgumentException("해당 클래스의 카테고리가 아닙니다.");
            }
        }
        
        boardCategoryService.moveCategory(id, newParentId);
        
        Map<String, String> resultMap = new HashMap<>();
        resultMap.put("result", "success");
        
        return ResponseEntity.ok(resultMap);
    }

    /**
     * 카테고리 활성화/비활성화
     * @param classId 클래스 ID
     * @param id 카테고리 ID
     * @param isActive 활성화 여부
     * @return 변경 결과
     */
    @Operation(summary = "카테고리 활성화/비활성화", description = "카테고리의 활성화 상태를 변경합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "변경 성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "404", description = "카테고리를 찾을 수 없음"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PatchMapping("/{id}/active")
    public ResponseEntity<Map<String, String>> setActive(
            @Parameter(description = "클래스 ID", required = true)
            @PathVariable("classId") Long classId,
            @Parameter(description = "카테고리 ID", required = true)
            @PathVariable("id") Long id,
            @Parameter(description = "활성화 여부", required = true)
            @RequestParam boolean isActive) {
        
        log.info("카테고리 활성화/비활성화 -> 클래스 ID: {}, 카테고리 ID: {}, 활성화 여부: {}", 
                classId, id, isActive);
        
        // 기존 카테고리 조회 및 클래스 ID 검증
        BoardCategoryDTO existingCategory = boardCategoryService.readOne(id);
        if (!classId.equals(existingCategory.getClassId())) {
            log.warn("권한 없는 접근 시도 - 요청 클래스 ID: {}, 실제 카테고리 클래스 ID: {}", 
                    classId, existingCategory.getClassId());
            throw new IllegalArgumentException("해당 클래스의 카테고리가 아닙니다.");
        }
        
        boardCategoryService.setActive(id, isActive);
        
        Map<String, String> resultMap = new HashMap<>();
        resultMap.put("result", "success");
        
        return ResponseEntity.ok(resultMap);
    }

    /**
     * 카테고리 추천 게시글 기준값 변경
     * @param classId 클래스 ID
     * @param id 카테고리 ID
     * @param threshold 새 추천 게시글 기준값
     * @return 변경 결과
     */
    @Operation(summary = "카테고리 추천 게시글 기준값 변경", description = "카테고리의 추천 게시글 기준값을 변경합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "변경 성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "404", description = "카테고리를 찾을 수 없음"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PatchMapping("/{id}/threshold")
    public ResponseEntity<Map<String, String>> changeRecommendThreshold(
            @Parameter(description = "클래스 ID", required = true)
            @PathVariable("classId") Long classId,
            @Parameter(description = "카테고리 ID", required = true)
            @PathVariable("id") Long id,
            @Parameter(description = "새 추천 게시글 기준값", required = true)
            @RequestParam int threshold) {
        
        log.info("카테고리 추천 게시글 기준값 변경 -> 클래스 ID: {}, 카테고리 ID: {}, 기준값: {}", 
                classId, id, threshold);
        
        // 기존 카테고리 조회 및 클래스 ID 검증
        BoardCategoryDTO existingCategory = boardCategoryService.readOne(id);
        if (!classId.equals(existingCategory.getClassId())) {
            log.warn("권한 없는 접근 시도 - 요청 클래스 ID: {}, 실제 카테고리 클래스 ID: {}", 
                    classId, existingCategory.getClassId());
            throw new IllegalArgumentException("해당 클래스의 카테고리가 아닙니다.");
        }
        
        boardCategoryService.changeRecommendThreshold(id, threshold);
        
        Map<String, String> resultMap = new HashMap<>();
        resultMap.put("result", "success");
        
        return ResponseEntity.ok(resultMap);
    }
}