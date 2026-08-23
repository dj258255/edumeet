package com.edu.edumeet.meeting.dto;

/**
 * 파이썬 STT 서버가 보내는 자막 조각. (#65)
 *
 * @param text     인식된 문장
 * @param sequence 발화 순서. 네트워크 재정렬을 클라이언트가 감지할 수 있어야 한다
 * @param spokenAt 원본 오디오에서의 발화 시각(epoch millis).
 *                 <b>서버 수신 시각이 아니다.</b> 이 둘의 차이가 곧 STT 지연이다
 */
public record CaptionIngestRequest(
        String text,
        Long sequence,
        Long spokenAt
) {}
