package com.edu.edumeet.openvidu.controller;

import com.edu.edumeet.openvidu.service.OpenviduService;
import io.livekit.server.WebhookReceiver;
import livekit.LivekitWebhook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LiveKit 서버가 보내는 이벤트를 받는다.
 *
 * <p><b>왜 필요한가</b> — 참가자가 브라우저를 그냥 닫으면 우리 서버는 알 방법이 없다.
 * 클라이언트가 "나갑니다"를 호출해주기를 기대할 수 없다. LiveKit 이 연결 종료를 감지해
 * {@code participant_left} 를 보내주므로, 이걸 받아야 <b>정원이 회수된다.</b> (#23)
 *
 * <p><b>인증</b> — 사용자 인증이 없어 `SecurityConfig` 에서 permitAll 이다.
 * 대신 {@link WebhookReceiver} 가 <b>API 시크릿으로 서명된 JWT</b> 를 검증한다.
 * 서명이 맞지 않으면 예외가 나고 401 을 돌려준다.
 *
 * <p><b>LiveKit 설정</b> — 서버 config 에 아래를 넣어야 이벤트가 온다.
 * <pre>
 * webhook:
 *   api_key: &lt;api_key&gt;
 *   urls:
 *     - http://&lt;this-server&gt;/api/v1/livekit/webhook
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/livekit")
public class LiveKitWebhookController {

    private final OpenviduService openviduService;
    private final WebhookReceiver webhookReceiver;

    public LiveKitWebhookController(OpenviduService openviduService,
                                    @Value("${openvidu.livekit.api.key}") String apiKey,
                                    @Value("${openvidu.livekit.api.secret}") String apiSecret) {
        this.openviduService = openviduService;
        this.webhookReceiver = new WebhookReceiver(apiKey, apiSecret);
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> receive(@RequestBody String body,
                                        @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        LivekitWebhook.WebhookEvent event;
        try {
            event = webhookReceiver.receive(body, authHeader);
        } catch (Exception e) {
            // 서명 검증 실패. 남의 요청이거나 시크릿이 틀렸다.
            log.warn("LiveKit webhook 서명 검증 실패", e);
            return ResponseEntity.status(401).build();
        }

        String type = event.getEvent();
        String roomName = event.hasRoom() ? event.getRoom().getName() : null;

        switch (type) {
            case "participant_left" -> {
                if (roomName != null && event.hasParticipant()) {
                    // identity 에 참가자 이메일을 넣고 있다. (createToken 의 setIdentity)
                    String identity = event.getParticipant().getIdentity();
                    openviduService.releaseParticipant(roomName, identity);
                    log.info("참가자 이탈 - room={}, identity={}", roomName, identity);
                }
            }
            case "room_finished" -> {
                if (roomName != null) {
                    openviduService.releaseRoom(roomName);
                }
            }
            default -> log.debug("처리하지 않는 LiveKit 이벤트: {}", type);
        }

        // 200 을 빨리 돌려준다. LiveKit 은 실패하면 재시도하므로 여기서 오래 걸리면 안 된다.
        return ResponseEntity.ok().build();
    }
}
