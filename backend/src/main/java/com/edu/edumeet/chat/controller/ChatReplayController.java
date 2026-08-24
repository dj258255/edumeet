package com.edu.edumeet.chat.controller;

import com.edu.edumeet.chat.dto.ChatReplayResponse;
import com.edu.edumeet.chat.service.ChatService;
import com.edu.edumeet.member.domain.SecurityMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 다시보기 채팅 조회. (#108)
 *
 * <p><b>{@link ChatController} 와 분리한 이유.</b>
 * 그쪽은 STOMP 전용({@code @MessageMapping})이라 REST 엔드포인트가 없다.
 * 실시간 발행과 과거 조회는 프로토콜도 인증 경로도 다르다 —
 * 실시간은 STOMP CONNECT 에서 JWT 를 보고, 여기는 HTTP 필터 체인이 본다.
 *
 * <p>재생 위치({@code offsetMillis}) 기준이다. 절대 시각이 아니다.
 */
@RestController
@RequestMapping("/api/v1/meeting/{meetingId}/chat")
@RequiredArgsConstructor
public class ChatReplayController {

    private final ChatService chatService;

    /**
     * 구간의 대화를 읽는다.
     *
     * <pre>
     *   GET /api/v1/meeting/7/chat/replay?from=0&amp;to=60000
     * </pre>
     *
     * <p>기본 구간을 1분으로 둔 이유 — 다시보기 플레이어는 재생 위치를 따라가며
     * 조금씩 물어보는 것이 맞다. 한 번에 다 주면 두 시간 방송에서 응답이 수 MB 가 된다.
     *
     * @param from 구간 시작(포함), 회의 시작 기준 밀리초
     * @param to   구간 끝(제외)
     */
    @GetMapping("/replay")
    public ResponseEntity<ChatReplayResponse> replay(
            @AuthenticationPrincipal SecurityMember member,
            @PathVariable Long meetingId,
            @RequestParam(defaultValue = "0") long from,
            @RequestParam(defaultValue = "60000") long to) {

        return ResponseEntity.ok(
                chatService.replay(meetingId, member.getEmail(), from, to));
    }
}
