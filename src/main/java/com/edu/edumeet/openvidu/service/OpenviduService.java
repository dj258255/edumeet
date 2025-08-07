package com.edu.edumeet.openvidu.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.time.Duration;
import io.livekit.server.AccessToken;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenviduService {

    // OpenVidu 인증 정보 (application.properties에 추가)
    @Value("${openvidu.livekit.api.key}")
    private String LIVEKIT_API_KEY;

    @Value("${openvidu.livekit.api.secret}")
    private String LIVEKIT_API_SECRET;

    @Value("${openvidu.url}")
    private String OPENVIDU_URL;

    /**
     * LiveKit 토큰 생성 (직접 생성)
     */
    public Map<String, Object> createToken(String roomName, String participantName) {
        try {
            // AccessToken 생성
            AccessToken token = new AccessToken(LIVEKIT_API_KEY, LIVEKIT_API_SECRET);
            token.setName(participantName);
            token.setIdentity(participantName);

            // 권한 부여
            token.addGrants(
                    new RoomJoin(true),
                    new RoomName(roomName)
            );

            // 토큰 유효시간 설정 (옵션)
            token.setTtl(Duration.ofHours(6).toMillis());

            String jwt = token.toJwt();

            log.info("Token created for participant: {} in room: {}", participantName, roomName);

            Map<String, Object> response = new HashMap<>();
            response.put("token", jwt);
            response.put("url", OPENVIDU_URL);
            response.put("roomName", roomName);
            response.put("participantName", participantName);

            return response;

        } catch (Exception e) {
            log.error("Failed to create token", e);
            throw new RuntimeException("Failed to create token: " + e.getMessage());
        }
    }

    /**
     * 룸 정보 조회 (OpenVidu Admin API 사용)
     */
    public Map<String, Object> getRoomInfo(String roomName) {
        RestTemplate restTemplate = new RestTemplate();

        String url = "http://localhost:7880/api/v1/rooms/" + roomName;


        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(LIVEKIT_API_KEY, LIVEKIT_API_SECRET);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    Map.class
            );
            return response.getBody();

        } catch (HttpClientErrorException.NotFound e) {
            log.info("Room not found: {}", roomName);
            return null;
        } catch (Exception e) {
            log.error("Failed to get room info", e);
            throw new RuntimeException("Failed to get room info: " + e.getMessage());
        }
    }
}
