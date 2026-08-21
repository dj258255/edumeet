package com.edu.edumeet.reply.controller;

import com.edu.edumeet.reply.service.ReplyService;
import com.edu.edumeet.board.presentation.dto.PageRequestDTO;
import com.edu.edumeet.board.presentation.dto.PageResponseDTO;
import com.edu.edumeet.reply.dto.ReplyDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/class/{classId}/boards/{boardId}/replies")
@Log4j2
@RequiredArgsConstructor
public class ReplyController {

    private final ReplyService replyService;

    /**
     * 댓글 등록
     * @param classId 클래스 ID
     * @param boardId 게시글 ID
     * @param replyDTO 등록할 댓글 정보
     * @param bindingResult 유효성 검사 결과
     * @return 등록된 댓글 ID
     * @throws BindException 유효성 검사 실패 시 발생
     */
    @Operation(summary = "댓글 등록", description = "POST 방식으로 댓글 등록 (parentReplyId가 있으면 대댓글)")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String,Long>> register(
            @Parameter(description = "클래스 ID", required = true)
            @PathVariable("classId") Long classId,
            @Parameter(description = "게시글 ID", required = true)
            @PathVariable("boardId") Long boardId,
            @Valid @RequestBody ReplyDTO replyDTO,
            BindingResult bindingResult) throws BindException {
        
        log.info("댓글 등록 - 클래스 ID: {}, 게시글 ID: {}, 댓글: {}", classId, boardId, replyDTO);

        if(bindingResult.hasErrors()){
            throw new BindException(bindingResult);
        }

        // URL 경로의 boardId로 설정
        replyDTO.setBoardId(boardId);
        
        // 부모 댓글 ID가 있는 경우 (대댓글)
        if (replyDTO.getParentReplyId() != null) {
            // 부모 댓글이 존재하는지 확인
            ReplyDTO parentReply = replyService.read(replyDTO.getParentReplyId());
            if (parentReply == null) {
                throw new IllegalArgumentException("부모 댓글이 존재하지 않습니다: " + replyDTO.getParentReplyId());
            }
            
            // 부모 댓글이 해당 게시글에 속하는지 확인
            if (!boardId.equals(parentReply.getBoardId())) {
                log.warn("잘못된 부모 댓글 - 요청 게시글 ID: {}, 부모 댓글 게시글 ID: {}", 
                        boardId, parentReply.getBoardId());
                throw new IllegalArgumentException("해당 게시글의 댓글이 아닙니다.");
            }
            
            // 부모 댓글이 이미 대댓글인 경우 (최대 1계층까지만 허용)
            if (parentReply.isChildReply()) {
                throw new IllegalArgumentException("대댓글에는 댓글을 달 수 없습니다. 최대 1계층까지만 허용됩니다.");
            }
            
            log.info("대댓글 등록 - 부모 댓글 ID: {}", replyDTO.getParentReplyId());
        }

        Long reply_id = replyService.register(replyDTO);
        
        Map<String,Long> resultMap = new HashMap<>();
        resultMap.put("reply_id", reply_id);
        
        return ResponseEntity.ok(resultMap);
    }
    
    /**
     * 특정 댓글에 대댓글 등록 (편의 메서드)
     * @param classId 클래스 ID
     * @param boardId 게시글 ID
     * @param parentId 부모 댓글 ID
     * @param replyDTO 등록할 대댓글 정보
     * @param bindingResult 유효성 검사 결과
     * @return 등록된 대댓글 ID
     * @throws BindException 유효성 검사 실패 시 발생
     */
    @Operation(summary = "대댓글 등록", description = "POST 방식으로 특정 댓글에 대댓글 등록")
    @PostMapping(value = "/{parentId}/reply", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String,Long>> registerChildReply(
            @Parameter(description = "클래스 ID", required = true)
            @PathVariable("classId") Long classId,
            @Parameter(description = "게시글 ID", required = true)
            @PathVariable("boardId") Long boardId,
            @Parameter(description = "부모 댓글 ID", required = true)
            @PathVariable("parentId") Long parentId,
            @Valid @RequestBody ReplyDTO replyDTO,
            BindingResult bindingResult) throws BindException {
        
        log.info("대댓글 등록 - 클래스 ID: {}, 게시글 ID: {}, 부모 댓글 ID: {}, 댓글: {}", 
                classId, boardId, parentId, replyDTO);

        if(bindingResult.hasErrors()){
            throw new BindException(bindingResult);
        }

        // URL 경로의 boardId와 parentId로 설정
        replyDTO.setBoardId(boardId);
        replyDTO.setParentReplyId(parentId);
        
        // 부모 댓글이 존재하는지 확인
        ReplyDTO parentReply = replyService.read(parentId);
        if (parentReply == null) {
            throw new IllegalArgumentException("부모 댓글이 존재하지 않습니다: " + parentId);
        }
        
        // 부모 댓글이 해당 게시글에 속하는지 확인
        if (!boardId.equals(parentReply.getBoardId())) {
            log.warn("잘못된 부모 댓글 - 요청 게시글 ID: {}, 부모 댓글 게시글 ID: {}", 
                    boardId, parentReply.getBoardId());
            throw new IllegalArgumentException("해당 게시글의 댓글이 아닙니다.");
        }
        
        // 부모 댓글이 이미 대댓글인 경우 (최대 1계층까지만 허용)
        if (parentReply.isChildReply()) {
            throw new IllegalArgumentException("대댓글에는 댓글을 달 수 없습니다. 최대 1계층까지만 허용됩니다.");
        }

        Long reply_id = replyService.register(replyDTO);
        
        Map<String,Long> resultMap = new HashMap<>();
        resultMap.put("reply_id", reply_id);
        
        return ResponseEntity.ok(resultMap);
    }
    
    /**
     * 댓글 조회
     * @param classId 클래스 ID
     * @param boardId 게시글 ID
     * @param replyId 조회할 댓글 ID
     * @return 조회된 댓글 정보
     */
    @Operation(summary = "댓글 조회", description = "GET 방식으로 특정 댓글 조회")
    @GetMapping("/{replyId}")
    public ResponseEntity<ReplyDTO> read(
            @Parameter(description = "클래스 ID", required = true)
            @PathVariable("classId") Long classId,
            @Parameter(description = "게시글 ID", required = true)
            @PathVariable("boardId") Long boardId,
            @Parameter(description = "댓글 ID", required = true)
            @PathVariable("replyId") Long replyId) {
        
        log.info("댓글 조회 - 클래스 ID: {}, 게시글 ID: {}, 댓글 ID: {}", classId, boardId, replyId);
        
        ReplyDTO replyDTO = replyService.read(replyId);
        
        // 댓글이 해당 게시글에 속하는지 확인
        if (!boardId.equals(replyDTO.getBoardId())) {
            log.warn("권한 없는 접근 시도 - 요청 게시글 ID: {}, 실제 댓글 게시글 ID: {}", 
                    boardId, replyDTO.getBoardId());
            throw new IllegalArgumentException("해당 게시글의 댓글이 아닙니다.");
        }
        
        return ResponseEntity.ok(replyDTO);
    }
    
    /**
     * 댓글 수정
     * @param classId 클래스 ID
     * @param boardId 게시글 ID
     * @param replyId 수정할 댓글 ID
     * @param replyDTO 수정할 댓글 정보
     * @param bindingResult 유효성 검사 결과
     * @return 성공 메시지
     * @throws BindException 유효성 검사 실패 시 발생
     */
    @Operation(summary = "댓글 수정", description = "PUT 방식으로 특정 댓글 수정")
    @PutMapping(value = "/{replyId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String,String>> modify(
            @Parameter(description = "클래스 ID", required = true)
            @PathVariable("classId") Long classId,
            @Parameter(description = "게시글 ID", required = true)
            @PathVariable("boardId") Long boardId,
            @Parameter(description = "댓글 ID", required = true)
            @PathVariable("replyId") Long replyId,
            @Valid @RequestBody ReplyDTO replyDTO,
            BindingResult bindingResult) throws BindException {
        
        log.info("댓글 수정 - 클래스 ID: {}, 게시글 ID: {}, 댓글 ID: {}, 수정 내용: {}", 
                classId, boardId, replyId, replyDTO);
        
        if(bindingResult.hasErrors()) {
            throw new BindException(bindingResult);
        }
        
        // 기존 댓글 정보 확인
        ReplyDTO existingReply = replyService.read(replyId);
        if (!boardId.equals(existingReply.getBoardId())) {
            log.warn("권한 없는 수정 시도 - 요청 게시글 ID: {}, 실제 댓글 게시글 ID: {}", 
                    boardId, existingReply.getBoardId());
            throw new IllegalArgumentException("해당 게시글의 댓글이 아닙니다.");
        }
        
        // 경로 변수의 값으로 ID 설정
        replyDTO.setId(replyId);
        replyDTO.setBoardId(boardId);
        
        replyService.modify(replyDTO);
        
        Map<String,String> resultMap = new HashMap<>();
        resultMap.put("result", "수정 완료");
        
        return ResponseEntity.ok(resultMap);
    }
    
    /**
     * 댓글 삭제
     * @param classId 클래스 ID
     * @param boardId 게시글 ID
     * @param replyId 삭제할 댓글 ID
     * @return 성공 메시지
     */
    @Operation(summary = "댓글 삭제", description = "DELETE 방식으로 특정 댓글 삭제")
    @DeleteMapping("/{replyId}")
    public ResponseEntity<Map<String,String>> remove(
            @Parameter(description = "클래스 ID", required = true)
            @PathVariable("classId") Long classId,
            @Parameter(description = "게시글 ID", required = true)
            @PathVariable("boardId") Long boardId,
            @Parameter(description = "댓글 ID", required = true)
            @PathVariable("replyId") Long replyId) {
        
        log.info("댓글 삭제 - 클래스 ID: {}, 게시글 ID: {}, 댓글 ID: {}", classId, boardId, replyId);
        
        // 삭제하기 전에 댓글이 해당 게시글에 속하는지 확인
        ReplyDTO existingReply = replyService.read(replyId);
        if (!boardId.equals(existingReply.getBoardId())) {
            log.warn("권한 없는 삭제 시도 - 요청 게시글 ID: {}, 실제 댓글 게시글 ID: {}", 
                    boardId, existingReply.getBoardId());
            throw new IllegalArgumentException("해당 게시글의 댓글이 아닙니다.");
        }
        
        replyService.remove(replyId);
        
        Map<String,String> resultMap = new HashMap<>();
        resultMap.put("result", "삭제 완료");
        
        return ResponseEntity.ok(resultMap);
    }
    
    /**
     * 특정 게시글의 댓글 목록 조회
     * @param classId 클래스 ID
     * @param boardId 게시글 ID
     * @param pageRequestDTO 페이지 요청 정보
     * @return 페이징된 댓글 목록
     */
    @Operation(summary = "게시글별 댓글 목록", description = "GET 방식으로 특정 게시글의 댓글 목록 조회")
    @GetMapping
    public ResponseEntity<PageResponseDTO<ReplyDTO>> getList(
            @Parameter(description = "클래스 ID", required = true)
            @PathVariable("classId") Long classId,
            @Parameter(description = "게시글 ID", required = true)
            @PathVariable("boardId") Long boardId,
            @Parameter(description = "페이지 요청 정보")
            PageRequestDTO pageRequestDTO) {
        
        log.info("게시글 댓글 목록 조회 - 클래스 ID: {}, 게시글 ID: {}, 페이지: {}", 
                classId, boardId, pageRequestDTO);
        
        PageResponseDTO<ReplyDTO> responseDTO = replyService.getListOfBoard(boardId, pageRequestDTO);
        
        return ResponseEntity.ok(responseDTO);
    }
    
    /**
     * 특정 게시글의 계층형 댓글 목록 조회
     * 최상위 댓글만 페이징하고, 각 최상위 댓글의 대댓글은 모두 포함
     * @param classId 클래스 ID
     * @param boardId 게시글 ID
     * @param pageRequestDTO 페이지 요청 정보
     * @return 페이징된 계층형 댓글 목록
     */
    @Operation(summary = "게시글별 계층형 댓글 목록", description = "GET 방식으로 특정 게시글의 계층형 댓글 목록 조회 (대댓글 포함)")
    @GetMapping("/hierarchical")
    public ResponseEntity<PageResponseDTO<ReplyDTO>> getHierarchicalList(
            @Parameter(description = "클래스 ID", required = true)
            @PathVariable("classId") Long classId,
            @Parameter(description = "게시글 ID", required = true)
            @PathVariable("boardId") Long boardId,
            @Parameter(description = "페이지 요청 정보")
            PageRequestDTO pageRequestDTO) {
        
        log.info("게시글 계층형 댓글 목록 조회 - 클래스 ID: {}, 게시글 ID: {}, 페이지: {}", 
                classId, boardId, pageRequestDTO);
        
        PageResponseDTO<ReplyDTO> responseDTO = replyService.getHierarchicalListOfBoard(boardId, pageRequestDTO);
        
        return ResponseEntity.ok(responseDTO);
    }
    
    /**
     * 특정 댓글의 대댓글 목록 조회
     * @param classId 클래스 ID
     * @param boardId 게시글 ID
     * @param parentId 부모 댓글 ID
     * @return 대댓글 목록
     */
    @Operation(summary = "댓글별 대댓글 목록", description = "GET 방식으로 특정 댓글의 대댓글 목록 조회")
    @GetMapping("/{parentId}/children")
    public ResponseEntity<List<ReplyDTO>> getChildReplies(
            @Parameter(description = "클래스 ID", required = true)
            @PathVariable("classId") Long classId,
            @Parameter(description = "게시글 ID", required = true)
            @PathVariable("boardId") Long boardId,
            @Parameter(description = "부모 댓글 ID", required = true)
            @PathVariable("parentId") Long parentId) {
        
        log.info("댓글 대댓글 목록 조회 - 클래스 ID: {}, 게시글 ID: {}, 부모 댓글 ID: {}", 
                classId, boardId, parentId);
        
        // 부모 댓글이 해당 게시글에 속하는지 확인
        ReplyDTO parentReply = replyService.read(parentId);
        if (!boardId.equals(parentReply.getBoardId())) {
            log.warn("권한 없는 접근 시도 - 요청 게시글 ID: {}, 실제 부모 댓글 게시글 ID: {}", 
                    boardId, parentReply.getBoardId());
            throw new IllegalArgumentException("해당 게시글의 댓글이 아닙니다.");
        }
        
        List<ReplyDTO> childReplies = replyService.getChildReplies(parentId);
        
        return ResponseEntity.ok(childReplies);
    }
}