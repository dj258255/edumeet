package com.edu.edumeet.chat.config;

import com.edu.edumeet.config.jwt.JwtService;
import com.edu.edumeet.meeting.repository.MeetingParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * STOMP 프레임 단계의 인증·인가. (#33)
 *
 * <h3>왜 HTTP 필터로 안 되는가</h3>
 * Spring Security 의 필터 체인은 <b>WebSocket 핸드셰이크(HTTP GET /ws)까지만</b> 관여한다.
 * 그 뒤로 흐르는 STOMP 프레임은 HTTP 요청이 아니므로 필터를 타지 않는다.
 * 핸드셰이크만 막으면 <b>연결 이후에는 아무 방이나 구독할 수 있다.</b>
 *
 * <h3>어디서 무엇을 보는가</h3>
 * <pre>
 *   CONNECT     JWT 검증 → Principal 설정        (내가 누구인가)
 *   SUBSCRIBE   방 참가 기록 확인                 (이 방을 볼 자격이 있는가)
 * </pre>
 *
 * SUBSCRIBE 를 막지 않으면 남의 회의 채팅을 그대로 받아볼 수 있다.
 * 연결당 한 번씩만 도는 경로라 여기서 DB 를 한 번 보는 비용은 받아들인다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    /**
     * 방 하나에 딸린 목적지들.
     *
     * <pre>
     *   /topic/rooms/{id}            채팅
     *   /topic/rooms/{id}/captions   실시간 자막 (#65)
     * </pre>
     *
     * <b>목적지를 나눈 이유</b> — 클라이언트가 자막만 구독하거나 끌 수 있어야 한다.
     * 하나로 합치면 채팅을 안 보는 사람도 채팅 트래픽을 받는다.
     *
     * <p>권한은 <b>둘 다 같다</b> — 이 방을 볼 수 있으면 채팅도 자막도 볼 수 있다.
     * 그래서 방 번호만 뽑아서 같은 검사를 태운다.
     */
    private static final Pattern ROOM_DESTINATION =
            Pattern.compile("^/topic/rooms/(\\d+)(?:/captions)?$");
    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final MeetingParticipantRepository meetingParticipantRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            throw new ChatAccessDeniedException("인증 토큰이 없습니다.");
        }
        String token = header.substring(BEARER.length());
        if (!jwtService.isTokenValid(token)) {
            throw new ChatAccessDeniedException("유효하지 않은 토큰입니다.");
        }

        String email = jwtService.extractUsername(token);
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                email, null, AuthorityUtils.createAuthorityList("ROLE_USER")));
        log.debug("STOMP 연결 - {}", email);
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new ChatAccessDeniedException("구독 대상이 없습니다.");
        }
        Matcher matcher = ROOM_DESTINATION.matcher(destination);
        if (!matcher.matches()) {
            // 방 목적지가 아니면 이 인터셉터가 판단할 일이 아니다.
            // 다만 알 수 없는 /topic 은 열어두지 않는다.
            throw new ChatAccessDeniedException("구독할 수 없는 대상입니다: " + destination);
        }

        Principal user = accessor.getUser();
        if (user == null) {
            throw new ChatAccessDeniedException("인증되지 않은 연결입니다.");
        }

        Long meetingId = Long.valueOf(matcher.group(1));
        boolean joined = meetingParticipantRepository
                .findActive(meetingId, user.getName())
                .isPresent();
        if (!joined) {
            log.warn("참가하지 않은 방 구독 시도 - meetingId={}, email={}", meetingId, user.getName());
            throw new ChatAccessDeniedException("참가하지 않은 회의의 채팅은 볼 수 없습니다.");
        }
    }
}
