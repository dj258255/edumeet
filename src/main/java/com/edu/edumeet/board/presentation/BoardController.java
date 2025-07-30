package com.edu.edumeet.board.presentation;


import com.edu.edumeet.board.presentation.dto.*;
import com.edu.edumeet.util.S3Uploader;
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
@RequestMapping("/api/v1/boards")
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
     * @param pageRequestDTO 페이지 요청 정보
     * @return 페이징된 게시글 목록
     */
    @Operation(summary = "게시글 목록 조회", description = "페이징 및 검색 조건으로 게시글 목록을 조회합니다 (댓글 수 포함)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공", 
                    content = @Content(schema = @Schema(implementation = PageResponseDTO.class))),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping
    public ResponseEntity<PageResponseDTO<BoardListAllDTO>> list(
            @Parameter(description = "페이지 요청 정보 (페이지 번호, 크기, 검색 조건)") 
            PageRequestDTO pageRequestDTO) {
        log.info("게시글 목록 조회: {}", pageRequestDTO);
        
        //PageResponseDTO<BoardListReplyCountDTO> responseDTO = boardService.listWithReplyCount(pageRequestDTO);
        PageResponseDTO<BoardListAllDTO> responseDTO = boardService.listWithAll(pageRequestDTO);
        log.info("responseDTO: {}", responseDTO);

        return ResponseEntity.ok(responseDTO);
    }

    /**
     * 게시글 등록
     * @param boardDTO 등록할 게시글 정보
     * @param bindingResult 유효성 검사 결과
     * @return 등록된 게시글 ID
     * @throws BindException 유효성 검사 실패 시 발생
     */
    @Operation(summary = "게시글 등록", description = "새로운 게시글을 등록합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "등록 성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping
    public ResponseEntity<Map<String, Long>> register(
            @Parameter(description = "등록할 게시글 정보", required = true) 
            @Valid @RequestBody BoardDTO boardDTO,
            BindingResult bindingResult) throws BindException {
        
        log.info("게시글 등록: {}", boardDTO);
        
        if(bindingResult.hasErrors()) {
            log.info("유효성 검사 오류: {}", bindingResult.getAllErrors());
            throw new BindException(bindingResult);
        }
        log.info(boardDTO);

        Long boardId = boardService.register(boardDTO);
        
        Map<String, Long> resultMap = new HashMap<>();
        resultMap.put("id", boardId);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(resultMap);
    }

    /**
     * 게시글 조회
     * @param id 조회할 게시글 ID
     * @return 조회된 게시글 정보
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
            @Parameter(description = "조회할 게시글 ID", required = true) 
            @PathVariable("id") Long id) {
        log.info("게시글 조회: {}", id);
        
        BoardDTO boardDTO = boardService.readOne(id);
        
        return ResponseEntity.ok(boardDTO);
    }

    /**
     * 게시글 수정
     * @param id 수정할 게시글 ID
     * @param boardDTO 수정할 게시글 정보
     * @param bindingResult 유효성 검사 결과
     * @return 성공 메시지
     * @throws BindException 유효성 검사 실패 시 발생
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
            @Parameter(description = "수정할 게시글 ID", required = true) 
            @PathVariable("id") Long id,
            @Parameter(description = "수정할 게시글 정보", required = true) 
            @Valid @RequestBody BoardDTO boardDTO,
            BindingResult bindingResult) throws BindException {
        
        log.info("게시글 수정: {}", boardDTO);
        
        if(bindingResult.hasErrors()) {
            log.info("유효성 검사 오류: {}", bindingResult.getAllErrors());
            throw new BindException(bindingResult);
        }
        
        // 경로 변수의 값으로 ID 설정
        boardDTO.setId(id);
        
        boardService.modify(boardDTO);
        
        Map<String, String> resultMap = new HashMap<>();
        resultMap.put("result", "수정 완료");
        
        return ResponseEntity.ok(resultMap);
    }

    /**
     * 게시글 삭제
     * @param id 삭제할 게시글 ID
     * @return 성공 메시지
     */
    @Operation(summary = "게시글 삭제", description = "특정 게시글을 삭제합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "삭제 성공"),
        @ApiResponse(responseCode = "404", description = "게시글을 찾을 수 없음"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(
            @Parameter(description = "삭제할 게시글 ID", required = true) 
            @PathVariable("id") Long id) {
        log.info("게시글 삭제: {}", id);

        //삭제하기전에 정보저장
        BoardDTO boardDTO = boardService.readOne(id);
        //삭제
        boardService.remove(id);

        //게시물이 삭제되었다면 첨부파일도 삭제
        log.info(boardDTO.getFileNames());
        List<String> fileNames = boardDTO.getFileNames();
        if(fileNames != null && !fileNames.isEmpty()) {
            removeFiles(fileNames);
        }


        return ResponseEntity.noContent().build();
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
