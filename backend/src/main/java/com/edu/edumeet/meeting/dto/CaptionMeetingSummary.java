package com.edu.edumeet.meeting.dto;

/**
 * 저장된 final 자막이 있는 회의 한 건. (#133)
 *
 * <p>MCP 서버가 "어느 회의를 볼 것인가" 를 먼저 물을 수 있게 하려고 만들었다.
 * 이것이 없으면 도구를 쓰는 쪽이 meetingId 를 이미 알고 있어야 한다.
 *
 * @param segmentCount final 자막 조각 수. 0 인 회의는 목록에 나오지 않는다
 * @param lastSpokenAt 마지막 발화 시각(epoch millis). 정렬 기준이다
 */
public record CaptionMeetingSummary(
        Long meetingId,
        String title,
        Long segmentCount,
        Long lastSpokenAt
) {}
