package com.edu.edumeet.chat.dto;

/**
 * 방으로 브로드캐스트되는 것. (#33)
 *
 * @param publishedAt 서버가 발행한 시각(epoch millis).
 *                    <b>end-to-end 지연은 기본 메트릭에 없다.</b> k6 가 수신 시각과 이 값을 빼서
 *                    커스텀 Trend 에 넣을 수 있도록 페이로드에 담는다.
 *                    이게 없으면 "서버가 처리한 시간" 만 보이고 "사용자가 받기까지" 는 안 보인다.
 */
public record ChatMessageResponse(
        Long meetingId,
        String sender,
        String content,
        long publishedAt
) {}
