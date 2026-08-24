package com.edu.edumeet.meeting.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * HLS 배포 설정. (#75)
 *
 * <p>{@code segmentDuration} 의 기본값을 <b>2초로 잡았다.</b>
 * LiveKit 의 기본값은 4초다({@code pkg/config/output_segment.go}).
 *
 * <p>HLS 재생 지연은 대략 <b>세그먼트 길이 × 플레이어 버퍼 개수</b>다.
 * 플레이어는 보통 3개를 채우고 재생을 시작하므로 4초면 12초, 2초면 6초에서 출발한다.
 * <b>공짜는 아니다</b> — 세그먼트가 짧아지면 요청 수가 두 배가 되고,
 * 매 세그먼트가 IDR 프레임으로 시작해야 하므로 키프레임이 잦아져 같은 화질에 비트레이트가 오른다.
 * 이 트레이드오프는 실측해서 {@code docs/performance} 에 남긴다.
 */
@Component
@ConfigurationProperties(prefix = "edumeet.hls")
@Getter
@Setter
public class HlsProperties {

    /** HLS 를 켤 것인가. egress 인스턴스가 없으면 꺼 둔다. */
    private boolean enabled = false;

    /** 세그먼트 길이(초). LiveKit 기본값은 4다. */
    private int segmentDuration = 2;

    /** 객체 스토리지에서 HLS 산출물이 놓일 최상위 경로. */
    private String prefix = "hls";

    /** 플레이어가 읽을 공개 기준 URL. 비어 있으면 스토리지 엔드포인트를 쓴다. */
    private String publicBaseUrl = "";
}
