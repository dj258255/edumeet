package com.edu.edumeet.board.presentation;


import com.edu.edumeet.board.presentation.dto.*;
import com.edu.edumeet.util.S3Uploader;
import io.lettuce.core.dynamic.annotation.Param;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 게시판 컨트롤러
 * RESTful API 방식으로 게시글 관련 요청을 처리
 */
@RestController
@RequestMapping("/api/v1/class/{classId}/boards")
@Log4j2
@RequiredArgsConstructor
@Tag(name = "게시판 API", description = "게시글 관련 API")
public class BoardController {

    //파일업로드경로
    @Value("${edumeet.upload.path}")
    private String uploadPath;

    private final S3Uploader s3Uploader;

    private final BoardService boardService;

    /**
     * 게시글 목록 조회
     * request : pagerequestDTO
     * response : 페이징된 게시글 목록
     */
//    @Operation(summary = "게시글 목록 조회", description = "페이징 및 검색 조건으로 게시글 목록을 조회합니다 (댓글 수 포함), 그리고 classId가 있으면 해당 클래스의 게시글만, 없으면 전체 게시글을 조회")
//    @ApiResponses({
//        @ApiResponse(responseCode = "200", description = "조회 성공",
//                    content = @Content(schema = @Schema(implementation = PageResponseDTO.class))),
//        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
//        @ApiResponse(responseCode = "500", description = "서버 오류")
//    })
//    @GetMapping
//    public ResponseEntity<PageResponseDTO<BoardListAllDTO>> list(
//            @Parameter(description = "페이지 요청 정보 (페이지 번호, 크기, 검색 조건)")
//            PageRequestDTO pageRequestDTO) {
//        log.info("게시글 목록 조회: {}", pageRequestDTO);
//
//        if(pageRequestDTO.getClassId() != null){
//            log.info("클래스별 게시글 목록 조회 -> 클래스 ID : {} , 페이지 정보: {}",
//                    pageRequestDTO.getClassId(), pageRequestDTO);
//        } else{
//            log.info("전체 게시글 목록 조회: {}" , pageRequestDTO);
//        }
//
//        //PageResponseDTO<BoardListReplyCountDTO> responseDTO = boardService.listWithReplyCount(pageRequestDTO);
//        PageResponseDTO<BoardListAllDTO> responseDTO = boardService.listWithAll(pageRequestDTO);
//        log.info("조회 결과 -> 총{} 건, 현재 페이지 : {}",
//                responseDTO
//                        .getTotal(), responseDTO.getPage());
//
//        return ResponseEntity.ok(responseDTO);
//    }


    @Operation(summary = "게시글 목록 조회", description = "클래스별 게시글 목록을 조회합니다. 카테고리 ID와 게시글 타입으로 필터링할 수 있습니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = PageResponseDTO.class))),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping
    public ResponseEntity<PageResponseDTO<BoardListAllDTO>> list(
            @Parameter(description = "클래스 ID", required = true)
            @PathVariable("classId") Long classId,
            @Parameter(description = "카테고리 ID (특정 카테고리의 게시글만 조회)")
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "게시글 타입 (NORMAL: 일반, NOTICE: 공지사항, RECOMMENDED: 추천게시글)")
            @RequestParam(required = false) String boardType,
            @Parameter(description = "페이지 요청 정보")
            PageRequestDTO pageRequestDTO){

        log.info("클래스별 게시글 목록 조회 -> 클래스 ID: {}, 카테고리 ID: {}, 게시글 타입: {}, 페이지 정보: {}", 
                classId, categoryId, boardType, pageRequestDTO);

        // pageRequestDTO에 필터링 조건 설정
        pageRequestDTO.setClassId(classId);

        PageResponseDTO<BoardListAllDTO> responseDTO = boardService.listWithAll(pageRequestDTO);
        
        // null 체크 추가
        if (responseDTO == null) {
            log.error("boardService.listWithAll()이 null을 반환했습니다. pageRequestDTO: {}", pageRequestDTO);
            throw new RuntimeException("게시글 목록 조회에 실패했습니다.");
        }
        
        log.info("조회 결과 -> 총 {} 건, 현재 페이지: {}", responseDTO.getTotal(), responseDTO.getPage());

        return ResponseEntity.ok(responseDTO);
    }


    /**
     * 특정 클래스에 게시글 등록
     */
    @Operation(summary = "게시글 등록", description = "새로운 게시글을 등록합니다")
    @PostMapping
    public ResponseEntity<Map<String, Long>> register(
            @Parameter(description = "클래스 ID", required = true)
            @PathVariable("classId") Long classId,
            @Parameter(description = "등록할 게시글 정보", required = true)
            @Valid @RequestBody BoardDTO boardDTO,
            BindingResult bindingResult) throws BindException {
        
        log.info("클래스별 게시글 등록 -> URL 클래스 ID : {} , 게시글 : {} ", classId, boardDTO);
        
        if(bindingResult.hasErrors()) {
            throw new BindException(bindingResult);
        }
        log.info(boardDTO);

        //urL 경로의 클래스Id가 중요
        boardDTO.setClassId(classId);

        Long boardId = boardService.register(boardDTO);
        
        Map<String, Long> resultMap = new HashMap<>();
        resultMap.put("id", boardId);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(resultMap);
    }

    /**
     * 게시글 조회
     * request: id 조회할 게시글 ID
     * response: 조회된 게시글 정보
     */
    @Operation(summary = "게시글 조회", description = "특정 게시글을 ID로 조회합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공", 
                    content = @Content(schema = @Schema(implementation = BoardDTO.class))),
        @ApiResponse(responseCode = "404", description = "게시글을 찾을 수 없음"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/{id}")
    public ResponseEntity<BoardDTO> read(
            @Parameter(description = "클래스 ID" , required = true)
            @PathVariable("classId") Long classId,
            @Parameter(description = "조회할 게시글 ID", required = true) 
            @PathVariable("id") Long id) {
        log.info("클래스별 게시글 조회 -> 클래스 ID: {}, 게시글 ID: {}", classId, id);

        BoardDTO boardDTO = boardService.readOne(id);

        if (!classId.equals(boardDTO.getClassId())) {
            log.warn("권한 없는 접근 시도 - 요청 클래스 ID: {}, 실제 게시글 클래스 ID: {}",
                    classId, boardDTO.getClassId());
            throw new IllegalArgumentException("해당 클래스의 게시글이 아닙니다.");
        }

        return ResponseEntity.ok(boardDTO);
    }

    /**
     * 특정 클래스의 게시글 수정
     */
    @Operation(summary = "게시글 수정", description = "특정 게시글을 수정합니다 request body에서 writer 없이 title, content만 보내주세요.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "수정 성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "404", description = "게시글을 찾을 수 없음"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> modify(
            @Parameter(description = "클래스 ID", required = true)
            @PathVariable("classId") Long classId,
            @Parameter(description = "수정할 게시글 ID", required = true) 
            @PathVariable("id") Long id,
            @Parameter(description = "수정할 게시글 정보", required = true) 
            @Valid @RequestBody BoardDTO boardDTO,
            BindingResult bindingResult) throws BindException {

        log.info("클래스별 게시글 수정 -> 클래스 ID: {}, 게시글 ID: {}, 수정 내용: {}", classId, id, boardDTO);

        if(bindingResult.hasErrors()) {
            log.info("유효성 검사 오류: {}", bindingResult.getAllErrors());
            throw new BindException(bindingResult);
        }

        BoardDTO existingBoard = boardService.readOne(id);
        if(!classId.equals(existingBoard.getClassId())) {
            log.warn("권한 없는 수정 시도 - 요청 클래스 ID: {}, 실제 게시글 클래스 ID: {}",  classId, existingBoard.getClassId());
            throw new IllegalArgumentException("해당 클래스의 게시글이 아닙니다.");
        }

        // 경로 변수의 값으로 ID 설정
        boardDTO.setId(id);
        boardDTO.setClassId(classId);

        boardService.modify(boardDTO);
        
        Map<String, String> resultMap = new HashMap<>();
        resultMap.put("result", "수정 완료");
        
        return ResponseEntity.ok(resultMap);
    }

    /**
     * 게시글 삭제 (논리적 삭제)
     */
    @Operation(summary = "게시글 삭제", description = "특정 게시글을 논리적으로 삭제합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "삭제 성공"),
        @ApiResponse(responseCode = "404", description = "게시글을 찾을 수 없음"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(
            @Parameter(description = "클래스 ID" , required = true)
            @PathVariable("classId") Long classId,
            @Parameter(description = "삭제할 게시글 ID", required = true) 
            @PathVariable("id") Long id) {

        log.info("클래스별 게시글 삭제 -> 클래스 ID: {}, 게시글 ID: {}", classId, id);

        //삭제하기전에 정보저장
        BoardDTO boardDTO = boardService.readOne(id);

        if (!classId.equals(boardDTO.getClassId())) {
            log.warn("권한 없는 삭제 시도 - 요청 클래스 ID: {}, 실제 게시글 클래스 ID: {}",
                    classId, boardDTO.getClassId());
            throw new IllegalArgumentException("해당 클래스의 게시글이 아닙니다.");
        }

        //논리적 삭제 (물리적 파일 삭제는 하지 않음)
        boardService.remove(id);

        // 논리적 삭제이므로 파일은 삭제하지 않음
        // 복원 시 파일을 다시 사용할 수 있도록 함
        // log.info(boardDTO.getFileNames());
        // List<String> fileNames = boardDTO.getFileNames();
        // if(fileNames != null && !fileNames.isEmpty()) {
        //     removeFiles(fileNames);
        // }


        return ResponseEntity.noContent().build();
    }
    
    /**
     * 삭제된 게시글 복원
     */
    @Operation(summary = "게시글 복원", description = "논리적으로 삭제된 게시글을 복원합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "복원 성공"),
        @ApiResponse(responseCode = "404", description = "게시글을 찾을 수 없음"),
        @ApiResponse(responseCode = "400", description = "이미 복원된 게시글"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PatchMapping("/{id}/restore")
    public ResponseEntity<Map<String, String>> restore(
            @Parameter(description = "클래스 ID", required = true)
            @PathVariable("classId") Long classId,
            @Parameter(description = "복원할 게시글 ID", required = true)
            @PathVariable("id") Long id) {
        
        log.info("게시글 복원 -> 클래스 ID: {}, 게시글 ID: {}", classId, id);
        
        try {
            // 게시글 복원
            boardService.restore(id);
            
            Map<String, String> result = new HashMap<>();
            result.put("result", "success");
            result.put("message", "게시글이 성공적으로 복원되었습니다.");
            
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            // 게시글을 찾을 수 없는 경우
            log.warn("게시글 복원 실패 - 게시글을 찾을 수 없음: {}", id);
            throw e;
        } catch (IllegalStateException e) {
            // 이미 복원된 게시글인 경우
            log.warn("게시글 복원 실패 - 이미 복원된 게시글: {}", id);
            throw e;
        }
    }

    //removeFiles를 S3 삭제로 수정

    public void removeFiles(List<String> files){
        for(String fileName : files){
            try{
                //s3에서 파일 삭제
                s3Uploader.removeS3File(fileName);

                //이미지 파일인 경우 섬네일도 삭제
                if(isImageFile(fileName)){
                    String thumbnailFileName = "s_" + fileName;
                    s3Uploader.removeS3File(thumbnailFileName);
                }
            } catch (Exception e){
                log.error("S3 파일 삭제 실패 : {}", e.getMessage());
            }
        }
    }


    //이미지 파일 확인 헬퍼 메서드
    private boolean isImageFile(String fileName){
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        return Arrays.asList("jpg", "jpeg", "png", "gif","bmp","webp").contains(extension);
    }
    
    /**
     * 게시글 좋아요 추가
     */
    @Operation(summary = "게시글 좋아요 추가", description = "게시글에 좋아요를 추가합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "좋아요 추가 성공"),
        @ApiResponse(responseCode = "404", description = "게시글을 찾을 수 없음"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/{id}/favorite")
    public ResponseEntity<Map<String, Long>> toggleFavorite(
            @Parameter(description = "클래스 ID", required = true)
            @PathVariable("classId") Long classId,
            @Parameter(description = "좋아요 추가할 게시글 ID", required = true) 
            @PathVariable("id") Long id) {
        
        log.info("게시글 좋아요 추가 -> 클래스 ID: {}, 게시글 ID: {}", classId, id);
        
        // 게시글이 해당 클래스에 속하는지 확인
        BoardDTO boardDTO = boardService.readOne(id);
        if (!classId.equals(boardDTO.getClassId())) {
            log.warn("권한 없는 좋아요 추가 시도 - 요청 클래스 ID: {}, 실제 게시글 클래스 ID: {}", 
                    classId, boardDTO.getClassId());
            throw new IllegalArgumentException("해당 클래스의 게시글이 아닙니다.");
        }
        
        // 좋아요 추가
        long favoriteCount = boardService.toggleFavorite(id);
        
        Map<String, Long> resultMap = new HashMap<>();
        resultMap.put("favoriteCount", favoriteCount);
        
        return ResponseEntity.ok(resultMap);
    }
    
    /**
     * 게시글 싫어요 추가
     */
    @Operation(summary = "게시글 싫어요 추가", description = "게시글에 싫어요를 추가합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "싫어요 추가 성공"),
        @ApiResponse(responseCode = "404", description = "게시글을 찾을 수 없음"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/{id}/dislike")
    public ResponseEntity<Map<String, Long>> toggleDislike(
            @Parameter(description = "클래스 ID", required = true)
            @PathVariable("classId") Long classId,
            @Parameter(description = "싫어요 추가할 게시글 ID", required = true) 
            @PathVariable("id") Long id) {
        
        log.info("게시글 싫어요 추가 -> 클래스 ID: {}, 게시글 ID: {}", classId, id);
        
        // 게시글이 해당 클래스에 속하는지 확인
        BoardDTO boardDTO = boardService.readOne(id);
        if (!classId.equals(boardDTO.getClassId())) {
            log.warn("권한 없는 싫어요 추가 시도 - 요청 클래스 ID: {}, 실제 게시글 클래스 ID: {}", 
                    classId, boardDTO.getClassId());
            throw new IllegalArgumentException("해당 클래스의 게시글이 아닙니다.");
        }
        
        // 싫어요 추가
        long dislikeCount = boardService.toggleDislike(id);
        
        Map<String, Long> resultMap = new HashMap<>();
        resultMap.put("dislikeCount", dislikeCount);
        
        return ResponseEntity.ok(resultMap);
    }

//    public void removeFiles(List<String> files) {
//        for(String fileName:files){
//            Resource resource = new FileSystemResource(uploadPath + File.separator + fileName);
//
//            String resourceName = resource.getFilename();
//
//            try{
//                String contentType = Files.probeContentType(resource.getFile().toPath());
//
//                resource.getFile().delete();
//
//                //썸네일이 존재하면
//                if(contentType.startsWith("image")){
//                    File thumbnailFile = new File(uploadPath + File.separator + "s_" + fileName);
//
//                    thumbnailFile.delete();
//                }
//            } catch (Exception e){
//                log.error(e.getMessage());
//            }
//        }
//    }
}