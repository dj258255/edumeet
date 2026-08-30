package com.edu.edumeet.meeting.broadcast;

import com.edu.edumeet.meeting.config.BroadcastProperties;
import com.edu.edumeet.meeting.domain.BroadcastCodecPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * ffmpeg 명령을 만든다. 순수 로직이라 ffmpeg 없이 시험한다. (#123)
 *
 * <p><b>리먹싱에는 우리가 못 정하는 것이 하나 있다 — 세그먼트 경계.</b>
 *
 * <p>HLS 세그먼트는 키프레임에서 시작해야 한다(Apple HLS 스펙 7.4). 그런데
 * {@code -c:v copy} 는 프레임을 그대로 옮기므로 <b>키프레임을 새로 만들 수 없다.</b>
 * 키프레임 간격은 브라우저의 인코더가 정한다. 그래서
 *
 * <pre>
 *   -c:v libx264  →  -g 로 키프레임을 강제할 수 있다. hls_time 이 지켜진다
 *   -c:v copy     →  강제할 수 없다. hls_time 은 "최소" 가 되고 실제는 더 길 수 있다
 * </pre>
 *
 * <b>즉 리먹싱은 CPU 를 아끼는 대신 세그먼트 길이의 통제권을 브라우저에 넘긴다.</b>
 * 공짜가 아니라 교환이다. 실제로 몇 초가 나오는지는 재 봐야 안다.
 */
public final class FfmpegCommand {

    private FfmpegCommand() {
    }

    /** 트랜스코딩할 때 가정하는 프레임률. 키프레임 간격 계산에만 쓴다. */
    private static final int ASSUMED_FPS = 30;

    public static List<String> build(BroadcastProperties props, BroadcastCodecPlan plan, String outputDir) {
        List<String> cmd = new ArrayList<>();
        cmd.add(props.getFfmpegPath());
        cmd.add("-hide_banner");
        cmd.add("-loglevel");
        cmd.add("warning");

        // 표준입력에서 읽는다. 브라우저가 보낸 조각을 이어 붙인 스트림이다.
        cmd.add("-i");
        cmd.add("pipe:0");

        cmd.addAll(plan.codecArgs());

        if (plan.transcodesVideo()) {
            // 다시 인코딩할 때만 키프레임을 강제할 수 있다.
            // 이걸 안 주면 x264 기본 간격(약 250프레임)이 걸려 세그먼트가 8초씩 나온다.
            int gop = props.getSegmentSeconds() * ASSUMED_FPS;
            cmd.add("-g");
            cmd.add(String.valueOf(gop));
            cmd.add("-keyint_min");
            cmd.add(String.valueOf(gop));
            // 장면 전환에서 키프레임을 끼워 넣으면 세그먼트 길이가 들쭉날쭉해진다.
            cmd.add("-sc_threshold");
            cmd.add("0");
        }

        cmd.add("-f");
        cmd.add("hls");
        cmd.add("-hls_time");
        cmd.add(String.valueOf(props.getSegmentSeconds()));
        cmd.add("-hls_list_size");
        cmd.add(String.valueOf(props.getPlaylistSize()));

        // delete_segments  오래된 파일을 지운다. 없으면 디스크가 방송 내내 찬다
        // omit_endlist     라이브라는 뜻. 없으면 플레이어가 VOD 로 보고 처음부터 튼다
        // independent_segments 각 세그먼트가 스스로 열린다고 선언한다
        //
        // ★ program_date_time — 세그먼트마다 "이 장면이 몇 시 것인가" 를 적는다. (#185)
        //
        //   이게 없으면 플레이어가 아는 것은 "재생 위치 12.3초" 뿐이고,
        //   그 화면이 몇 시에 촬영된 것인지는 모른다.
        //
        //   자막은 WebSocket 으로 1초 안에 도착하는데 영상은 HLS 라 몇 초 뒤에 온다.
        //   그래서 자막이 화면보다 **먼저** 뜬다 - 말하는 입 모양과 글자가 어긋난다.
        //   맞추려면 "지금 보여 주는 화면이 몇 시 것인가" 를 알아야 하는데,
        //   그걸 알려 주는 것이 이 태그다(EXT-X-PROGRAM-DATE-TIME).
        //
        //   비용이 거의 없다 - 세그먼트마다 한 줄이 늘 뿐이고 인코딩은 그대로다.
        cmd.add("-hls_flags");
        cmd.add("delete_segments+omit_endlist+independent_segments+program_date_time");

        cmd.add("-hls_segment_type");
        cmd.add("mpegts");
        cmd.add("-hls_segment_filename");
        cmd.add(outputDir + "/seg_%05d.ts");
        cmd.add(outputDir + "/live.m3u8");
        return cmd;
    }
}
