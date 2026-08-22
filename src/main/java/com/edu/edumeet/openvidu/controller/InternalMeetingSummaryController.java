package com.edu.edumeet.openvidu.controller;

import com.edu.edumeet.openvidu.dto.SummaryUploadResult;
import com.edu.edumeet.openvidu.service.MeetingSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 파이썬 AI 서버 전용. 사용자가 부르는 API 가 아니다. (#27)
 *
 * <p>경로를 {@code /api/v1/internal/} 로 분리한 이유는 보안 규칙을 섞지 않기 위해서다.
 * SecurityConfig 에서 이 접두사는 {@code hasRole("INTERNAL")} 이고,
 * {@link com.edu.edumeet.config.internal.InternalApiTokenFilter} 가
 * {@code X-Internal-Token} 헤더를 검사해서 그 권한을 준다.
 *
 * <h3>파이썬이 맞춰야 하는 규약</h3>
 * <pre>
 * POST /api/v1/internal/meetings/{meetingId}/summary
 *   X-Internal-Token: &lt;공유 시크릿&gt;
 *   Content-Type: multipart/form-data
 *   summary_md  : (선택) .md 파일
 *   summary_pdf : (선택) .pdf 파일
 *   → 둘 중 최소 하나는 있어야 한다. 빈 파일은 없는 것으로 본다.
 *
 * 201 Created : 이번 호출로 기록됐다
 * 200 OK      : 이미 있어서 아무것도 바꾸지 않았다 (재시도로 간주)
 * 400         : 파일이 없거나 형식/크기가 잘못됨, 또는 없는 meetingId
 * 401         : 토큰 불일치
 * </pre>
 *
 * <p>{@code meetingId} 는 <b>필수</b>다. 이전 구현은 없으면 최신 회의에 덮어쓰거나
 * 회의를 새로 만들었는데, 열린 적 없는 회의가 생기고 엉뚱한 회의에 요약본이 붙었다.
 */
@RestController
@RequestMapping("/api/v1/internal/meetings")
@RequiredArgsConstructor
@Slf4j
public class InternalMeetingSummaryController {

    private final MeetingSummaryService meetingSummaryService;

    @PostMapping("/{meetingId}/summary")
    public ResponseEntity<SummaryUploadResult> uploadSummary(
            @PathVariable("meetingId") Long meetingId,
            @RequestParam(value = "summary_md", required = false) MultipartFile summaryMd,
            @RequestParam(value = "summary_pdf", required = false) MultipartFile summaryPdf) {

        log.info("요약본 업로드 요청 - meetingId={}, md={}, pdf={}",
                meetingId,
                summaryMd == null ? null : summaryMd.getSize(),
                summaryPdf == null ? null : summaryPdf.getSize());

        SummaryUploadResult result = meetingSummaryService.uploadSummary(meetingId, summaryMd, summaryPdf);

        // 재시도로 아무것도 안 바뀌었으면 200, 이번에 기록했으면 201.
        // 파이썬이 재시도 성공과 최초 성공을 구분할 수 있어야 로그가 읽힌다.
        return ResponseEntity
                .status(result.alreadyExisted() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(result);
    }
}
