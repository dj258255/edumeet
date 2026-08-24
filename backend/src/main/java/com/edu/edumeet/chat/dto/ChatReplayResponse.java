package com.edu.edumeet.chat.dto;

import java.util.List;

/**
 * 다시보기 채팅 한 구간. (#108)
 *
 * @param meetingId   회의
 * @param fromMillis  요청한 구간 시작 (회의 시작 기준)
 * @param toMillis    요청한 구간 끝
 * @param messages    그 구간의 대화. offsetMillis 오름차순
 * @param hasMore     상한에 걸려 잘렸는가.
 *                    <b>false 를 "이게 전부" 로 읽으면 안 된다</b> - 정확히 상한만큼
 *                    있을 때도 false 가 된다. 클라이언트는 마지막 offset 부터 다시 물어야 한다.
 */
public record ChatReplayResponse(
        Long meetingId,
        long fromMillis,
        long toMillis,
        List<Message> messages,
        boolean hasMore
) {
    /**
     * @param offsetMillis 회의 시작 기준 경과 밀리초. 재생 위치와 맞추는 값이다
     */
    public record Message(String sender, String content, long offsetMillis) {}
}
