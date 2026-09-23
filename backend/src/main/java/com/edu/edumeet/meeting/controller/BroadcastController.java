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
     *             {@code segmentType} 은 {@code mpegts|fmp4}, {@code hlsTimeSec} 은
     *             {@code 1|2} 이고 둘 다 생략하면 기본값(fMP4·2초)을 쓴다.
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> start(
            @AuthenticationPrincipal SecurityMember member,
            @PathVariable Long meetingId,
            @RequestBody Map<String, String> body) {
        String segmentType = parseSegmentType(body.get("segmentType"));
        Integer hlsTimeSec = parseHlsTimeSec(body.get("hlsTimeSec"));
        String playlistUrl = broadcastService.start(
                member.getEmail(), meetingId, stringValue(body.get("mimeType")), segmentType, hlsTimeSec);
        return ResponseEntity.ok(Map.of("playlistUrl", playlistUrl));
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 조각 형식. 안 보내면 <b>fMP4</b> 다. (#198)
     *
     * <p><b>근거 (원격 송출·시청 10대 · 변형 6개 × 정상·제한 × 2회차)</b>
     *
     * <pre>
     *                정상 지연 p50      제한 끊김/명      첫 화면
     *   TS·2초(이전)  8.91 · 10.10초   36.2 · 34.3초   2,983 · 2,902ms
     *   fMP4·2초      9.13 ·  9.87초   31.6 · 30.7초   2,482 · 2,698ms   ← 채택
     *   1초 조각들     6.3 ~  8.0초    39.4 ~ 45.2초        -
     * </pre>
     *
     * <p>지연은 TS 와 사실상 같고 <b>끊김이 −12%</b>, 첫 화면이 약 350ms 짧다.
     * 1초 조각은 지연을 줄이지만 끊김이 +14~29% 늘어 기각했다 —
     * 판단 기준은 "끊김이 늘지 않는 범위에서만 지연을 줄인다" 였다.
     * (송출 1초·서버 조각 2초도 시도했지만 지연을 줄이지 못했다. 서버 조각은 다음 키프레임에서 닫혀
     * 송출 단위와 무관하다.)
     *
     * <p>표와 측정 조건: {@code docs/performance/29-playback-qoe-crosscheck.md}
     *
     * <p>{@code mpegts} 를 <b>명시하면 그대로 TS</b> 다 - 되돌리기와 A/B 측정에 필요하다.
     */
    static String parseSegmentType(Object value) {
        String segmentType = value == null ? "fmp4" : stringValue(value);
        if (!"mpegts".equals(segmentType) && !"fmp4".equals(segmentType)) {
            throw new IllegalArgumentException("segmentType 은 mpegts 또는 fmp4 이어야 합니다.");
        }
        return segmentType;
    }

    static Integer parseHlsTimeSec(Object value) {
        if (value == null) return null;
        final int hlsTimeSec;
        try {
            hlsTimeSec = Integer.parseInt(stringValue(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("hlsTimeSec 은 1 또는 2 이어야 합니다.");
        }
        if (hlsTimeSec != 1 && hlsTimeSec != 2) {
            throw new IllegalArgumentException("hlsTimeSec 은 1 또는 2 이어야 합니다.");
        }
        return hlsTimeSec;
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
