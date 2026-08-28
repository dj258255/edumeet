package com.edu.edumeet.meeting.dto;

/**
 * 시청자에게 나가는 자막. (#65)
 *
 * <h3>시각을 세 개나 담는 이유</h3>
 * <b>어디서 시간이 가는지 나눠서 재기 위해서다.</b> 하나로 합치면
 * "자막이 느리다" 는 말은 나오는데 <b>무엇을 고쳐야 하는지는 안 나온다.</b>
 *
 * <pre>
 *   spokenAt   ──STT 추론──> receivedAt ──자바 처리──> publishedAt ──전송──> 수신 시각
 *              └─ STT 지연 ─┘          └─ 홉 비용 ─┘  └─ 전달 지연 ─┘
 * </pre>
 *
 * <p>파이썬이 자바를 거치는 구조라 <b>"홉 하나가 자막을 느리게 만들지 않나"</b> 는 물음이 생긴다.
 * 예산을 나눠보면 STT 가 1.5~5초인데 홉은 밀리초 단위라 무시할 수준으로 보이지만,
 * <b>보이는 것과 잰 것은 다르다.</b> 그래서 나눠서 담는다.
 *
 * @param spokenAt    원본 오디오에서의 발화 시각
 * @param receivedAt  자바가 요청을 받은 시각
 * @param publishedAt 자바가 브로드캐스트한 시각
 * @param finalSegment 저장·요약에 쓸 수 있는 최종 자막인가
 * @param replay       끊겼다 다시 붙은 사람에게 구멍을 메우려고 다시 보낸 것인가. (#165)
 *                     <b>받는 쪽이 이것을 실시간 자막 줄에 띄우면 안 된다.</b>
 *                     지나간 구간을 채우는 것이라 전사 영역에 붙어야 한다.
 *                     표시가 없으면 받는 쪽이 실시간과 복구본을 구분할 방법이 없다.
 */
public record CaptionBroadcast(
        Long meetingId,
        String text,
        Long sequence,
        Long spokenAt,
        Long receivedAt,
        Long publishedAt,
        Boolean finalSegment,
        Boolean replay
) {
    /** 실시간 발행용. 복구본이 아니다. */
    public static CaptionBroadcast live(Long meetingId, String text, Long sequence,
                                        Long spokenAt, Long receivedAt, Long publishedAt,
                                        Boolean finalSegment) {
        return new CaptionBroadcast(meetingId, text, sequence, spokenAt,
                receivedAt, publishedAt, finalSegment, false);
    }

    /** 같은 내용을 복구본으로 표시해 되돌려 준다. */
    public CaptionBroadcast asReplay() {
        return new CaptionBroadcast(meetingId, text, sequence, spokenAt,
                receivedAt, publishedAt, finalSegment, true);
    }
}
