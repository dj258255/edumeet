package com.edu.edumeet.reply.presentation;

import com.edu.edumeet.board.presentation.dto.PageRequestDTO;
import com.edu.edumeet.board.presentation.dto.PageResponseDTO;
import com.edu.edumeet.reply.presentation.dto.ReplyDTO;
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
@RequestMapping("/replies")
@Log4j2
@RequiredArgsConstructor
public class ReplyController {

    private final ReplyService replyService;

    /**
     * 댓글 등록
     * @param replyDTO 등록할 댓글 정보
     * @param bindingResult 유효성 검사 결과
     * @return 등록된 댓글 ID
     * @throws BindException 유효성 검사 실패 시 발생
     */
    @Operation(summary = "댓글 등록", description = "POST 방식으로 댓글 등록")
    @PostMapping(value = "/", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String,Long>> register(
            @Valid @RequestBody ReplyDTO replyDTO,
            BindingResult bindingResult) throws BindException {
        
        log.info("댓글 등록: {}", replyDTO);

        if(bindingResult.hasErrors()){
            throw new BindException(bindingResult);
        }

        Long rno = replyService.register(replyDTO);
        
        Map<String,Long> resultMap = new HashMap<>();
        resultMap.put("rno", rno);
        
        return ResponseEntity.ok(resultMap);
    }
    
    /**
     * 댓글 조회
     * @param rno 조회할 댓글 ID
     * @return 조회된 댓글 정보
     */
    @Operation(summary = "댓글 조회", description = "GET 방식으로 특정 댓글 조회")
    @GetMapping("/{rno}")
    public ResponseEntity<ReplyDTO> read(@PathVariable("rno") Long rno) {
        log.info("댓글 조회: {}", rno);
        
        ReplyDTO replyDTO = replyService.read(rno);
        
        return ResponseEntity.ok(replyDTO);
    }
    
    /**
     * 댓글 수정
     * @param rno 수정할 댓글 ID
     * @param replyDTO 수정할 댓글 정보
     * @param bindingResult 유효성 검사 결과
     * @return 성공 메시지
     * @throws BindException 유효성 검사 실패 시 발생
     */
    @Operation(summary = "댓글 수정", description = "PUT 방식으로 특정 댓글 수정")
    @PutMapping(value = "/{rno}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String,String>> modify(
            @PathVariable("rno") Long rno,
            @Valid @RequestBody ReplyDTO replyDTO,
            BindingResult bindingResult) throws BindException {
        
        log.info("댓글 수정: {}", replyDTO);
        
        if(bindingResult.hasErrors()) {
            throw new BindException(bindingResult);
        }
        
        replyDTO.setId(rno); // 경로 변수의 값으로 ID 설정
        
        replyService.modify(replyDTO);
        
        Map<String,String> resultMap = new HashMap<>();
        resultMap.put("result", "수정 완료");
        
        return ResponseEntity.ok(resultMap);
    }
    
    /**
     * 댓글 삭제
     * @param rno 삭제할 댓글 ID
     * @return 성공 메시지
     */
    @Operation(summary = "댓글 삭제", description = "DELETE 방식으로 특정 댓글 삭제")
    @DeleteMapping("/{rno}")
    public ResponseEntity<Map<String,String>> remove(@PathVariable("rno") Long rno) {
        log.info("댓글 삭제: {}", rno);
        
        replyService.remove(rno);
        
        Map<String,String> resultMap = new HashMap<>();
        resultMap.put("result", "삭제 완료");
        
        return ResponseEntity.ok(resultMap);
    }
    
    /**
     * 특정 게시글의 댓글 목록 조회
     * @param bno 게시글 ID
     * @param pageRequestDTO 페이지 요청 정보
     * @return 페이징된 댓글 목록
     */
    @Operation(summary = "게시글별 댓글 목록", description = "GET 방식으로 특정 게시글의 댓글 목록 조회")
    @GetMapping("/list/{bno}")
    public ResponseEntity<PageResponseDTO<ReplyDTO>> getList(
            @PathVariable("bno") Long bno,
            PageRequestDTO pageRequestDTO) {
        
        log.info("게시글 {}의 댓글 목록 조회, 페이지: {}", bno, pageRequestDTO);
        
        PageResponseDTO<ReplyDTO> responseDTO = replyService.getListOfBoard(bno, pageRequestDTO);
        
        return ResponseEntity.ok(responseDTO);
    }
}
