package com.edu.edumeet.meeting.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 브라우저가 보낸 코덱으로 ffmpeg 인자를 정한다. (#123)
 *
 * <p><b>여기가 "거의 공짜" 와 "비쌈" 을 가르는 자리다.</b>
 *
 * <pre>
 *   브라우저가 H264 를 주면   -c:v copy      컨테이너만 바꾼다. 디코딩·인코딩이 없다
 *   VP8 만 주면              -c:v libx264   전부 다시 인코딩한다. 2코어에서 아프다
 * </pre>
 *
 * <p>브라우저마다 다르므로 <b>고정하지 않고 받은 값으로 정하고, 어느 쪽으로 갔는지 남긴다.</b>
 * 조용히 트랜스코딩하면 CPU 가 왜 튀는지 나중에 못 찾는다.
 *
 * <p><b>오디오는 거의 항상 다시 인코딩한다.</b> MediaRecorder 의 webm 은 Opus 를 담는데
 * MPEG-TS 는 Opus 를 표준으로 담지 못한다. 다만 오디오 인코딩은 비디오와 비교가 안 되게 싸다.
 */
public record BroadcastCodecPlan(
        boolean videoCopied,
        boolean audioCopied,
        boolean audioOnly,
        String sourceMimeType
) {

    private static final String H264_MARKERS = "avc1|avc3|h264";
    private static final String AAC_MARKERS = "mp4a|aac";

    /**
     * @param mimeType MediaRecorder 가 실제로 고른 값. 예)
     *                 {@code video/mp4;codecs=avc1.42E01E,mp4a.40.2}
     *                 {@code video/webm;codecs=vp8,opus}
     */
    public static BroadcastCodecPlan of(String mimeType, SessionType sessionType) {
        String normalized = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        boolean audioOnly = sessionType == SessionType.AUDIO_BROADCAST;

        // 오디오 전용이면 비디오 얘기는 의미가 없다. copy 여부를 false 로 두어
        // "비디오를 복사했다" 는 잘못된 보고가 나가지 않게 한다.
        boolean videoCopied = !audioOnly && containsAny(normalized, H264_MARKERS);
        boolean audioCopied = containsAny(normalized, AAC_MARKERS);

        return new BroadcastCodecPlan(videoCopied, audioCopied, audioOnly, mimeType);
    }

    private static boolean containsAny(String haystack, String pipeSeparated) {
        for (String needle : pipeSeparated.split("\\|")) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 비디오를 다시 인코딩해야 하는가.
     *
     * <p>이것이 참이면 CPU 가 코어 하나를 통째로 먹을 수 있다. 상한을 정할 때 이 값을 본다.
     */
    public boolean transcodesVideo() {
        return !audioOnly && !videoCopied;
    }

    /** ffmpeg 의 코덱 인자. 입력·출력 인자는 파이프라인이 붙인다. */
    public List<String> codecArgs() {
        List<String> args = new ArrayList<>();

        if (audioOnly) {
            // 비디오 트랙이 아예 없거나 무시한다. 라디오에 화면을 실어 보낼 이유가 없다.
            args.add("-vn");
        } else if (videoCopied) {
            args.add("-c:v");
            args.add("copy");
        } else {
            args.add("-c:v");
            args.add("libx264");
            // veryfast 를 고른 이유: 우리는 2코어이고 실시간이라 인코딩이 재생 속도를
            // 못 따라가면 지연이 계속 벌어진다. 화질보다 못 밀리는 것이 먼저다.
            args.add("-preset");
            args.add("veryfast");
            args.add("-tune");
            args.add("zerolatency");
        }

        if (audioCopied) {
            args.add("-c:a");
            args.add("copy");
        } else {
            args.add("-c:a");
            args.add("aac");
            args.add("-b:a");
            args.add(audioOnly ? "96k" : "128k");
        }
        return args;
    }

    /** 로그·측정에 쓰는 한 줄 요약. */
    public String describe() {
        if (audioOnly) {
            return "오디오전용/" + (audioCopied ? "복사" : "AAC재인코딩");
        }
        return (videoCopied ? "비디오복사(리먹싱)" : "비디오재인코딩(libx264)")
                + "+" + (audioCopied ? "오디오복사" : "AAC재인코딩");
    }
}
