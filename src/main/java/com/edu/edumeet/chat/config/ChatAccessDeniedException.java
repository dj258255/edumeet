package com.edu.edumeet.chat.config;

/**
 * STOMP 단계에서 거절한다. (#33)
 *
 * <p>{@code ChannelInterceptor} 에서 던지면 Spring 이 클라이언트에 ERROR 프레임을 보내고
 * 연결을 끊는다. HTTP 상태 코드가 없는 세계라 <b>메시지가 곧 응답</b>이다.
 */
public class ChatAccessDeniedException extends RuntimeException {
    public ChatAccessDeniedException(String message) {
        super(message);
    }
}
