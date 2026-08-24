package com.edu.edumeet.meeting.controller;

import com.edu.edumeet.meeting.broadcast.BroadcastService;
import com.edu.edumeet.member.domain.SecurityMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 자체 HLS 송출 제어. (#123)
 *
 * <p>세션 생성과 분리한 이유 — <b>방송 시작은 세션 시작과 다른 사건이다.</b>
 * 방을 열어 두고 준비하다가 송출만 나중에 켜는 흐름이 정상이다.
 *
 * <p><b>청크는 WebSocket 이 아니라 HTTP 로 받는다.</b> 이 서비스에서 WebSocket 은 채팅만 쓴다.
 * 미디어까지 그 위에 얹으면 채팅 지연이 미디어 상태에 묶여, 방송이 밀릴 때 채팅도 같이 밀린다.
 * 둘을 다른 경로에 두면 한쪽이 막혀도 다른 쪽은 산다.
 */
@RestController
@RequestMapping("/api/v1/meeting/{meetingId}/broadcast")
@RequiredArgsConstructor
public class BroadcastController {

    private final BroadcastService broadcastService;

    /**
     * 송출을 시작한다.
     *
     * @param body {@code mimeType} — MediaRecorder 가 <b>실제로 고른</b> 값.
     *             요청한 값이 아니라 {@code recorder.mimeType} 를 그대로 보내야 한다.
     *             이 값으로 리먹싱이냐 재인코딩이냐가 갈린다.
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> start(
            @AuthenticationPrincipal SecurityMember member,
            @PathVariable Long meetingId,
            @RequestBody Map<String, String> body) {
        String playlistUrl = broadcastService.start(member.getEmail(), meetingId, body.get("mimeType"));
        return ResponseEntity.ok(Map.of("playlistUrl", playlistUrl));
    }

    /**
     * 미디어 조각 하나를 받는다.
     *
     * <p><b>202 가 아니라 429 를 쓰는 자리가 있다.</b> ffmpeg 가 못 따라가면 큐가 차는데,
     * 그때 200 을 주면 발표자는 잘 나가는 줄 알고 계속 보낸다. 거부해야 클라이언트가 안다.
     */
    @PostMapping(value = "/chunk", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Void> chunk(
            @PathVariable Long meetingId,
            @RequestParam long seq,
            @RequestBody byte[] data) {
        boolean accepted = broadcastService.acceptChunk(meetingId, seq, data);
        return accepted
                ? ResponseEntity.accepted().build()
                : ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
    }

    @DeleteMapping
    public ResponseEntity<Map<String, String>> stop(
            @AuthenticationPrincipal SecurityMember member,
            @PathVariable Long meetingId) {
        broadcastService.stop(member.getEmail(), meetingId);
        return ResponseEntity.ok(Map.of("message", "송출을 중지했습니다."));
    }
}
