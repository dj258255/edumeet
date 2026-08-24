package com.edu.edumeet.meeting.config;

import io.livekit.server.RoomServiceClient;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LiveKit 관리 API 클라이언트.
 *
 * <p>이전에는 {@code RestTemplate} 으로 {@code GET /api/v1/rooms/{name}} 을 Basic Auth 로
 * 불렀다. <b>그 주소도 그 인증 방식도 LiveKit 에는 없다.</b> OpenVidu 시절 코드가 남아
 * 있었던 것으로, 실제 LiveKit 서버에서는 항상 401 이 돌아온다.
 * LiveKit 은 Twirp RPC 경로에 Bearer JWT 를 요구한다.
 *
 * <p>직접 맞추는 대신 공식 SDK 의 {@link RoomServiceClient} 를 쓴다.
 * 경로·인증·직렬화를 SDK 가 처리하므로 우리가 프로토콜을 따라갈 필요가 없다.
 *
 * <p>SDK 는 OkHttp 를 쓰는데 <b>기본 타임아웃이 10초</b>다. 룸 조회는 LiveKit 이
 * 메모리에서 답하는 가벼운 작업이라 그만큼 기다릴 이유가 없다.
 * {@code RestClientConfig} 와 같은 기준(연결 2초, 읽기 3초)으로 맞춘다.
 */
@Configuration
public class LiveKitConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    @Value("${livekit.url}")
    private String host;

    @Value("${livekit.api.key}")
    private String apiKey;

    @Value("${livekit.api.secret}")
    private String apiSecret;

    /**
     * egress 시작은 룸 조회보다 오래 걸린다. (#75)
     *
     * <p>룸 조회는 LiveKit 이 메모리에서 답하지만, egress 시작은
     * <b>Redis 를 거쳐 워커가 잡을 받아야</b> 응답이 온다.
     * 3초를 그대로 쓰면 정상 시작을 타임아웃으로 오인해
     * <b>egress 는 돌고 있는데 우리는 실패로 처리하는</b> 상태가 된다 -
     * 그러면 코어를 먹는 고아 egress 가 남는다.
     */

    @Bean
    public RoomServiceClient roomServiceClient() {
        return RoomServiceClient.createClient(host, apiKey, apiSecret, () ->
                httpClient(READ_TIMEOUT));
    }


    private OkHttpClient httpClient(Duration readTimeout) {
        return new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT)
                .readTimeout(readTimeout)
                .writeTimeout(readTimeout)
                .build();
    }
}
