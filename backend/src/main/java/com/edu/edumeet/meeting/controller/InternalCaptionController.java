package com.edu.edumeet.meeting.controller;

import com.edu.edumeet.meeting.dto.CaptionBroadcast;
import com.edu.edumeet.meeting.dto.CaptionIngestRequest;
import com.edu.edumeet.meeting.dto.CaptionTranscriptResponse;
import com.edu.edumeet.meeting.service.CaptionService;
import com.edu.edumeet.meeting.service.CaptionTranscriptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 파이썬 STT 서버 전용. 실시간 자막 입구. (#65)
 *
 * <pre>
 * POST /api/v1/internal/meetings/{meetingId}/captions
 *   X-Internal-Token: &lt;공유 시크릿&gt;
 *   { "text": "안녕하세요", "sequence": 42, "spokenAt": 1756... }
 *
 * 200 OK   시각 세 개가 담긴 브로드캐스트 결과
 * 400      빈 자막 · 너무 김 · 없는 회의 · 종료된 회의
 * 401      토큰 불일치
 * </pre>
 *
 * <p><b>동기 응답이지만 파이썬은 기다리지 않아야 한다.</b>
 * 자막은 던지고 잊는 것이 맞다 — 응답을 기다리면 STT 루프가 전송에 묶인다.
 * 응답 본문은 <b>측정용</b>이다(#65 질문 3-a).
 */
@RestController
@RequestMapping("/api/v1/internal/meetings")
@RequiredArgsConstructor
@Slf4j
public class InternalCaptionController {

    private final CaptionService captionService;
    private final CaptionTranscriptService captionTranscriptService;

    @PostMapping("/{meetingId}/captions")
    public ResponseEntity<CaptionBroadcast> ingest(
            @PathVariable("meetingId") Long meetingId,
            @RequestBody CaptionIngestRequest request) {

        // 수신 시각을 가장 먼저 찍는다. 여기서 늦게 찍으면 홉 비용이 실제보다 작게 나온다.
        long receivedAt = System.currentTimeMillis();
        return ResponseEntity.ok(captionService.broadcast(meetingId, request, receivedAt));
    }

    @GetMapping("/{meetingId}/captions/transcript")
    public ResponseEntity<CaptionTranscriptResponse> transcript(@PathVariable("meetingId") Long meetingId) {
        return ResponseEntity.ok(captionTranscriptService.buildTranscript(meetingId));
    }
}
