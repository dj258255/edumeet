package com.edu.edumeet.meeting.dto;

import java.util.List;

/**
 * 자막이 있는 회의 목록. (#133)
 *
 * <p>배열을 그대로 내보내지 않고 객체로 감싼다. 나중에 커서나 총계를 붙일 때
 * 최상위가 배열이면 호환을 깨지 않고는 필드를 더할 수 없다.
 *
 * @param returned 이번 응답에 담긴 수. limit 에 걸렸는지 부르는 쪽이 알 수 있다
 */
public record CaptionMeetingsResponse(
        List<CaptionMeetingSummary> meetings,
        int returned
) {
    public static CaptionMeetingsResponse of(List<CaptionMeetingSummary> meetings) {
        return new CaptionMeetingsResponse(meetings, meetings.size());
    }
}
