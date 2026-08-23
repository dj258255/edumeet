package com.edu.edumeet.perf;

import com.edu.edumeet.meeting.exception.LiveKitUnavailableException;
import com.edu.edumeet.meeting.service.MeetingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 외부 호출 타임아웃이 실제로 동작하는지 확인한다.
 *
 * <p>운영 엔드포인트({@code /api/v1/meetingroom/room/{roomName}})는 인증을 요구한다.
 * 장애 주입 실험에서 토큰을 돌리는 것은 측정 대상을 흐리므로 같은 서비스 메서드를
 * 인증 없이 부르는 경로를 둔다. <b>호출하는 코드는 운영과 동일하다.</b>
 *
 * <p>응답에 소요 시간과 결과 종류를 담아 부하/장애 스크립트가 판정할 수 있게 한다.
 */
@Profile("perf")
@RestController
@RequestMapping("/api/perf/livekit")
@RequiredArgsConstructor
public class ResilienceBenchmarkController {

    private final MeetingService meetingService;

    @Value("${livekit.url}")
    private String livekitUrl;

    @Value("${livekit.api.key}")
    private String apiKey;

    @Value("${livekit.api.secret}")
    private String apiSecret;

    @GetMapping("/room")
    public ResponseEntity<Map<String, Object>> room(
            @RequestParam(defaultValue = "meeting-1") String name,
            @RequestParam(defaultValue = "false") boolean legacy,
            // #62 의 접근 권한 검사를 통과할 이메일. 통과하지 못하면 이 벤치마크는
            // LiveKit 에 닿기도 전에 끊겨서 타임아웃이 아니라 권한 거부를 재게 된다.
            @RequestParam(defaultValue = PerfDataSeeder.PERF_OWNER_EMAIL) String email) {

        if (legacy) {
            return legacyRoom(name);
        }

        long start = System.nanoTime();
        Map<String, Object> body = new HashMap<>();
        HttpStatus status;

        try {
            Map<String, Object> info = meetingService.getRoomInfo(name, email);
            // 룸이 없는 것은 정상 결과다. 장애가 아니다.
            status = info == null ? HttpStatus.NOT_FOUND : HttpStatus.OK;
            body.put("outcome", info == null ? "ROOM_NOT_FOUND" : "OK");

        } catch (LiveKitUnavailableException e) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            body.put("outcome", "LIVEKIT_UNAVAILABLE");
            body.put("cause", e.getCause() == null ? null : e.getCause().getClass().getSimpleName());
        }

        body.put("elapsedMs", (System.nanoTime() - start) / 1_000_000);
        body.put("mode", "fixed");
        return ResponseEntity.status(status).body(body);
    }

    /**
     * 수정 전 구현. 대조군이다.
     *
     * <p>{@code new RestTemplate()} 의 {@code SimpleClientHttpRequestFactory} 는
     * connect/read timeout 이 둘 다 {@code -1}(무한)이다. 상대가 응답하지 않으면
     * <b>이 스레드는 영원히 돌아오지 않는다.</b> 타임아웃을 넣은 것이 실제로 무엇을
     * 막고 있는지 보이려면 막지 않았을 때를 함께 재야 한다.
     *
     * <p>이 경로는 perf 프로파일에만 존재한다.
     */
    private ResponseEntity<Map<String, Object>> legacyRoom(String name) {
        long start = System.nanoTime();
        Map<String, Object> body = new HashMap<>();
        HttpStatus status;

        RestTemplate legacyTemplate = new RestTemplate();   // 타임아웃 없음
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(apiKey, apiSecret);
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            legacyTemplate.exchange(livekitUrl + "/api/v1/rooms/" + name,
                    HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            status = HttpStatus.OK;
            body.put("outcome", "OK");
        } catch (HttpClientErrorException.NotFound e) {
            status = HttpStatus.NOT_FOUND;
            body.put("outcome", "ROOM_NOT_FOUND");
        } catch (Exception e) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            body.put("outcome", "ERROR");
            body.put("cause", e.getClass().getSimpleName());
        }

        body.put("elapsedMs", (System.nanoTime() - start) / 1_000_000);
        body.put("mode", "legacy");
        return ResponseEntity.status(status).body(body);
    }
}
