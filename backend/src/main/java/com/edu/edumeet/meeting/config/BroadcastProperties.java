package com.edu.edumeet.meeting.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 자체 HLS 송출 설정. (#123)
 *
 * <p><b>LiveKit egress 를 걷어내고 직접 만든다.</b> egress 의 {@code RoomComposite} 가
 * CPU 4를 요구하는 이유는 여러 참가자 화면을 헤드리스 Chrome 으로 렌더링해 <b>합성</b>하기
 * 때문인데, <b>방송 모드는 발표자 한 명만 나가므로 합성할 것이 없다.</b>
 * 합성이 필요 없는데 합성기를 쓰고 있었다.
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "edumeet.broadcast")
public class BroadcastProperties {

    /** ffmpeg 실행 파일. PATH 에 없으면 절대경로를 준다. */
    private String ffmpegPath = "ffmpeg";

    /** 세그먼트 하나의 길이(초). 지연이 여기에 비례한다. */
    private int segmentSeconds = 2;

    /**
     * 라이브 플레이리스트에 남기는 세그먼트 수.
     *
     * <p><b>0 으로 두면 플레이리스트가 방송 내내 무한히 커지고 디스크도 안 지워진다.</b>
     * 대신 이 값이 작을수록 시청자가 뒤로 감을 수 있는 폭이 줄어든다.
     */
    private int playlistSize = 6;

    /** 세그먼트를 쓰는 최상위 디렉터리. */
    private String outputDir = "/tmp/edumeet-hls";

    /** 시청자에게 주는 공개 주소의 앞부분. nginx 가 outputDir 을 여기로 서빙한다. */
    private String publicBaseUrl = "/hls";

    /**
     * 동시에 돌릴 수 있는 방송 수.
     *
     * <p><b>상한이 없으면 2코어에서 방송 세 개만 겹쳐도 서버가 멈춘다.</b>
     * 거부는 아프지만 전체가 멈추는 것보다 낫다.
     */
    private int maxConcurrent = 2;

    /**
     * 이 시간 동안 청크가 안 오면 방송을 끝낸다.
     *
     * <p><b>이게 없으면 발표자 브라우저가 그냥 닫혔을 때 ffmpeg 가 영원히 남는다.</b>
     * 종료 요청은 오지 않을 수 있다고 가정해야 한다.
     */
    private Duration idleTimeout = Duration.ofSeconds(30);

    /** 청크 하나의 최대 크기. 이보다 크면 거부한다. */
    private int maxChunkBytes = 8 * 1024 * 1024;

    /**
     * 순서가 뒤바뀐 청크를 기다려 주는 개수.
     *
     * <p>HTTP 는 순서를 보장하지 않는다. 브라우저가 연결을 여러 개 쓰면
     * 3번이 2번보다 먼저 도착할 수 있다. <b>그대로 ffmpeg 에 넣으면 스트림이 깨진다.</b>
     */
    private int reorderWindow = 4;
}
