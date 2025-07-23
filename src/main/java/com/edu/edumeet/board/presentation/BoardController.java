package com.edu.edumeet.board.presentation;


import com.edu.edumeet.board.presentation.dto.BoardDTO;
import com.edu.edumeet.board.presentation.dto.BoardListReplyCountDTO;
import com.edu.edumeet.board.presentation.dto.PageRequestDTO;
import com.edu.edumeet.board.presentation.dto.PageResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/boards")
@Log4j2
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    /**
     * 게시글 목록 조회
     * @param pageRequestDTO 페이지 요청 정보
     * @return 페이징된 게시글 목록
     */
    @Operation(summary = "게시글 목록 조회", description = "GET 방식으로 게시글 목록 조회 (댓글 수 포함)")
    @GetMapping
    public ResponseEntity<PageResponseDTO<BoardListReplyCountDTO>> list(PageRequestDTO pageRequestDTO) {
        log.info("게시글 목록 조회: {}", pageRequestDTO);
        
        PageResponseDTO<BoardListReplyCountDTO> responseDTO = boardService.listWithReplyCount(pageRequestDTO);
        
        return ResponseEntity.ok(responseDTO);
    }

    /**
     * 게시글 등록
     * @param boardDTO 등록할 게시글 정보
     * @param bindingResult 유효성 검사 결과
     * @return 등록된 게시글 ID
     * @throws BindException 유효성 검사 실패 시 발생
     */
    @Operation(summary = "게시글 등록", description = "POST 방식으로 게시글 등록")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Long>> register(
            @Valid @RequestBody BoardDTO boardDTO,
            BindingResult bindingResult) throws BindException {
        
        log.info("게시글 등록: {}", boardDTO);
        
        if(bindingResult.hasErrors()) {
            log.info("유효성 검사 오류: {}", bindingResult.getAllErrors());
            throw new BindException(bindingResult);
        }
        
        Long bno = boardService.register(boardDTO);
        
        Map<String, Long> resultMap = new HashMap<>();
        resultMap.put("bno", bno);
        
        return ResponseEntity.ok(resultMap);
    }

    /**
     * 게시글 조회
     * @param bno 조회할 게시글 ID
     * @return 조회된 게시글 정보
     */
    @Operation(summary = "게시글 조회", description = "GET 방식으로 특정 게시글 조회")
    @GetMapping("/{bno}")
    public ResponseEntity<BoardDTO> read(@PathVariable("bno") Long bno) {
        log.info("게시글 조회: {}", bno);
        
        BoardDTO boardDTO = boardService.readOne(bno);
        
        return ResponseEntity.ok(boardDTO);
    }

    /**
     * 게시글 수정
     * @param bno 수정할 게시글 ID
     * @param boardDTO 수정할 게시글 정보
     * @param bindingResult 유효성 검사 결과
     * @return 성공 메시지
     * @throws BindException 유효성 검사 실패 시 발생
     */
    @Operation(summary = "게시글 수정", description = "PUT 방식으로 특정 게시글 수정")
    @PutMapping(value = "/{bno}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> modify(
            @PathVariable("bno") Long bno,
            @Valid @RequestBody BoardDTO boardDTO,
            BindingResult bindingResult) throws BindException {
        
        log.info("게시글 수정: {}", boardDTO);
        
        if(bindingResult.hasErrors()) {
            log.info("유효성 검사 오류: {}", bindingResult.getAllErrors());
            throw new BindException(bindingResult);
        }
        
        boardDTO.setId(bno); // 경로 변수의 값으로 ID 설정
        
        boardService.modify(boardDTO);
        
        Map<String, String> resultMap = new HashMap<>();
        resultMap.put("result", "수정 완료");
        
        return ResponseEntity.ok(resultMap);
    }

    /**
     * 게시글 삭제
     * @param bno 삭제할 게시글 ID
     * @return 성공 메시지
     */
    @Operation(summary = "게시글 삭제", description = "DELETE 방식으로 특정 게시글 삭제")
    @DeleteMapping("/{bno}")
    public ResponseEntity<Map<String, String>> remove(@PathVariable("bno") Long bno) {
        log.info("게시글 삭제: {}", bno);
        
        boardService.remove(bno);
        
        Map<String, String> resultMap = new HashMap<>();
        resultMap.put("result", "삭제 완료");
        
        return ResponseEntity.ok(resultMap);
    }
}
