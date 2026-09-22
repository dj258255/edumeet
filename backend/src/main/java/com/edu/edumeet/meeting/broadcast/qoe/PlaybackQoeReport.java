package com.edu.edumeet.meeting.broadcast.qoe;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 시청자 브라우저가 보내는 누적 요약 한 건. (#197)
 *
 * <p><b>왜 30초 누적 요약인가.</b> 끊김 이벤트만 보내면 분모(끊김 없이 본 시간)가 없어
 * 리버퍼율을 못 낸다. 매초 스냅샷을 보내면 300명에 초당 300건이다.
 *
 * <p><b>{@code last}/{@code nativePlayer} 의 JSON 이름을 바꾸는 이유.</b>
 * 프론트 본문은 {@code final} · {@code native} 라는 이름을 쓴다. {@code native} 는
 * 자바 예약어라 필드 이름으로 못 쓰고, {@code final} 은 의미가 겹친다.
 * 그래서 자바 필드는 {@code last} · {@code nativePlayer} 로 두고 JSON 이름만 맞춘다.
 *
 * <p><b>신원 정보는 없다.</b> 사용자·이메일·닉네임은 싣지 않는다. 세션을 가르는 것은
 * 브라우저가 만든 {@code sessionId} 뿐이고, 회의 단위는 경로 변수({@code meetingId})가 만든다.
 */
public record PlaybackQoeReport(
        String sessionId,
        int seq,
        long intervalMs,
        long playingMs,
        long stallMs,
        long stallCount,
        Long startupMs,
        int errors,
        @JsonProperty("final") boolean last,
        @JsonProperty("native") boolean nativePlayer
) {
}
