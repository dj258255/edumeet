package com.edu.edumeet.meeting.hls;

import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.domain.SessionType;
import livekit.LivekitEgress.S3Upload;
import livekit.LivekitEgress.SegmentedFileOutput;
import livekit.LivekitEgress.SegmentedFileProtocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HLS egress 요청을 만든다. 네트워크를 타지 않는 순수 계산이다. (#75)
 *
 * <p>여기에 모아 둔 이유는 <b>HLS 의 실패가 대부분 조용하기 때문이다.</b>
 * 잘못 만든 요청은 에러를 내지 않고 <b>이상한 방송</b>을 만든다 —
 * 라이브인데 처음부터 재생되거나, 플레이리스트가 무한히 커지거나, 지연이 12초에서 시작한다.
 * 조용한 실패는 테스트로 고정해야 한다.
 */
@Component
public class HlsEgressPlanner {

    /** S3Config 와 같은 자리표시자. 이 값이면 스토리지가 설정되지 않은 것으로 본다. */
    private static final String NOT_CONFIGURED = "not-configured";

    private final HlsProperties properties;
    private final String accessKey;
    private final String secretKey;
    private final String region;
    private final String endpoint;
    private final String bucket;

    /**
     * <b>생성자 주입이다.</b> 필드에 {@code @Value} 를 달면 Spring 컨텍스트 없이는
     * 인스턴스를 만들 수 없어서, "네트워크를 타지 않는 순수 계산" 이라는 이 클래스의 목적이
     * 테스트에서 무너진다. 생성자로 받으면 {@code new} 로 만들어 바로 검증할 수 있다.
     */
    public HlsEgressPlanner(
            HlsProperties properties,
            @Value("${spring.cloud.aws.credentials.access-key:}") String accessKey,
            @Value("${spring.cloud.aws.credentials.secret-key:}") String secretKey,
            @Value("${spring.cloud.aws.region.static:auto}") String region,
            @Value("${spring.cloud.aws.s3.endpoint:}") String endpoint,
            @Value("${spring.cloud.aws.s3.bucket:}") String bucket) {
        this.properties = properties;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.region = region;
        this.endpoint = endpoint;
        this.bucket = bucket;
    }

    /** 플레이어가 여는 파일. 최근 세그먼트만 담긴다. */
    public static final String LIVE_PLAYLIST = "live.m3u8";

    /** 다시보기용. 세그먼트가 계속 누적되고 {@code EXT-X-PLAYLIST-TYPE:EVENT} 가 붙는다. */
    public static final String VOD_PLAYLIST = "index.m3u8";

    /**
     * 이 세션을 HLS 로 내보낼 수 있는가.
     *
     * <p><b>{@code INTERACTIVE} 는 거부한다.</b> 못 해서가 아니라 <b>하면 안 되기 때문이다.</b>
     * 화상강의의 존재 이유는 1초 미만 지연이고 HLS 는 최소 6초에서 출발한다.
     * 손을 들고 6초 뒤에 지목받는 수업은 수업이 아니다.
     */
    public boolean supports(SessionType sessionType) {
        return sessionType != SessionType.INTERACTIVE;
    }

    public HlsEgressPlan plan(Meeting meeting) {
        SessionType type = meeting.getSessionType();
        if (!supports(type)) {
            throw new IllegalArgumentException(
                    "화상강의는 HLS 로 내보내지 않습니다. 1초 미만 지연이 목적인데 HLS 는 6초부터 시작합니다.");
        }

        String directory = "%s/meeting-%d".formatted(trimSlashes(properties.getPrefix()), meeting.getId());

        SegmentedFileOutput.Builder output = SegmentedFileOutput.newBuilder()
                .setProtocol(SegmentedFileProtocol.HLS_PROTOCOL)
                .setFilenamePrefix(directory + "/segment")

                // 다시보기용 플레이리스트.
                .setPlaylistName(directory + "/" + VOD_PLAYLIST)

                // ★ 이걸 비워 두면 라이브 방송에 VOD 플레이리스트가 나간다.
                //   에러가 아니다. 재생은 되는데 방송 시작점부터 재생되고
                //   플레이리스트가 방송 내내 무한히 커진다.
                //   LiveKit 은 live_playlist_name 이 없으면 라이브 플레이리스트를 아예 안 만든다.
                .setLivePlaylistName(directory + "/" + LIVE_PLAYLIST)

                // ★ 기본값 4초를 그대로 두지 않는다. 지연이 세그먼트 길이에 비례한다.
                //   LiveKit 은 세그먼트 출력에서 키프레임 간격을 세그먼트 길이에 자동으로 맞춘다
                //   (Apple HLS 스펙 7.4 "Video segments MUST start with an IDR frame").
                //   그래서 이 값만 바꾸면 되고, key_frame_interval 을 따로 주면 오히려 깨진다.
                .setSegmentDuration(properties.getSegmentDuration());

        if (hasStorage()) {
            output.setS3(storage());
        }

        // ★ 오디오 전용은 CPU 비용이 4에서 1로 떨어진다. 아껴서가 아니라 파이프라인이 다르다 -
        //   헤드리스 Chrome 합성을 건너뛰고 SDK 소스를 직접 쓴다.
        //   우리 서버는 2 OCPU 라, 이 플래그가 "방송이 되냐 안 되냐" 를 가른다.
        boolean audioOnly = type.isAudioOnly();

        return new HlsEgressPlan("meeting-" + meeting.getId(), output.build(), audioOnly);
    }

    /**
     * R2 는 S3 호환이지만 <b>가상 호스트 스타일 주소를 받지 않는다.</b>
     * {@code forcePathStyle} 을 켜지 않으면 egress 가 업로드 단계에서 조용히 실패한다.
     * ({@code S3Presigner} 에서 이미 한 번 겪은 것과 같은 문제다.)
     */
    private boolean hasStorage() {
        return !bucket.isBlank() && !NOT_CONFIGURED.equals(bucket);
    }

    private S3Upload storage() {
        S3Upload.Builder s3 = S3Upload.newBuilder()
                .setAccessKey(accessKey)
                .setSecret(secretKey)
                .setRegion(region)
                .setBucket(bucket);
        if (!endpoint.isBlank()) {
            s3.setEndpoint(endpoint).setForcePathStyle(true);
        }
        return s3.build();
    }

    /** 플레이어에게 줄 라이브 플레이리스트 주소. */
    public String livePlaylistUrl(Long meetingId) {
        String base = properties.getPublicBaseUrl().isBlank()
                ? "%s/%s".formatted(trimSlashes(endpoint), bucket)
                : trimSlashes(properties.getPublicBaseUrl());
        return "%s/%s/meeting-%d/%s"
                .formatted(base, trimSlashes(properties.getPrefix()), meetingId, LIVE_PLAYLIST);
    }

    private static String trimSlashes(String value) {
        return value.replaceAll("^/+", "").replaceAll("/+$", "");
    }
}
