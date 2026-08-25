package com.edu.edumeet.meeting.dto;

/**
 * 저장된 final 자막으로 만든 회의 transcript. (#131)
 *
 * @param segmentCount 요약 입력에 들어간 final 자막 조각 수
 * @param generatedAt  transcript 를 만든 시각(epoch millis)
 */
public record CaptionTranscriptResponse(
        Long meetingId,
        int segmentCount,
        String text,
        Long generatedAt
) {}
