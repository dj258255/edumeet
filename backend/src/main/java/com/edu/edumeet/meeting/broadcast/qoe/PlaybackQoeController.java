package com.edu.edumeet.meeting.broadcast.qoe;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시청 품질 보고를 받는다. (#197)
 *
 * <h3>왜 STOMP 가 아니라 별도 HTTP 인가</h3>
 *
 * <p>이 서비스에서 STOMP 는 채팅·자막용이다. 미디어·채팅 경로를 나눈 이유와 같다 -
 * 한쪽이 밀려도 다른 쪽은 산다. 그리고 여기서 <b>경로로 골라 버릴 수 있어야</b> 한다.
 * 나중에 우선순위를 정할 때(조각 업로드 vs 품질 보고) 입구가 다르면 정하기 쉽다.
 *
 * <h3>왜 방송 중인지 확인하지 않나</h3>
 *
 * <p>보고마다 DB 조회가 붙는다. 30초마다 300명이면 초당 10건이고, <b>품질을 재려고
 * 서비스의 읽기 부하를 올리는 셈</b>이다. 보고는 최선 노력(best effort) 데이터다 -
 * 방송이 아닐 때 온 보고도 합계를 조금 흔들 뿐이다. 검증(상한)이 그 흔들림을 막는다.
 *
 * <h3>경로 변수 이름이 {@code meetingId} 인 이유</h3>
 *
 * <p>로그의 회의 번호는 {@code MeetingIdLogInterceptor} 가 <b>경로 변수 이름</b>으로 붙인다.
 * {@code {meetingId}} 로 쓰면 자동으로 붙고, {@code {id}} 로 쓰면 안 붙는다. (#205)
 * 그래서 여기서는 값을 직접 쓰지 않아도 이름을 지킨다.
 */
@RestController
@RequestMapping("/api/v1/meeting/{meetingId}/broadcast")
@RequiredArgsConstructor
public class PlaybackQoeController {

    private final PlaybackQoeRecorder recorder;

    /** 유효하면 202, 상한을 넘으면 400. 본문은 없다. */
    @PostMapping("/qoe")
    public ResponseEntity<Void> report(
            @PathVariable Long meetingId,
            @RequestBody PlaybackQoeReport report) {
        return recorder.record(report)
                ? ResponseEntity.accepted().build()
                : ResponseEntity.badRequest().build();
    }
}
