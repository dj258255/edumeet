package com.edu.edumeet.config.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/**
 * 스프링이 해석한 경로 변수에서 로그의 회의 번호를 붙인다. (#205)
 *
 * <p><b>왜 필터가 아니라 인터셉터인가.</b> 필터 단계에는 어느 핸들러로 갈지 모른다.
 * URL 문자열로 추측하면 {@code /meetingroom/{classId}} 와
 * {@code /meetingroom/{meetingId}} 를 가르지 못한다. 틀린 번호는 없는 번호보다 나쁘다.
 *
 * <p><b>잃는 것.</b> 보안 필터처럼 핸들러 매핑 전에 찍히는 로그에는 붙지 않는다.
 *
 * <p><b>새 경로를 만들 때.</b> 경로 변수 이름을 {@code {meetingId}} 로 쓰면 자동으로 붙는다.
 * {@code {id}} 로 쓰면 안 붙는다.
 */
public class MeetingIdLogInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (attribute instanceof Map<?, ?> variables) {
            Object value = variables.get(MeetingLogContextFilter.MEETING_ID);
            if (value instanceof String meetingId && meetingId.matches("\\d+")) {
                MDC.put(MeetingLogContextFilter.MEETING_ID, meetingId);
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception exception) {
        // 핸들러에서 예외가 나도 Spring MVC 가 afterCompletion 을 호출하므로 여기서 반드시 지운다.
        MDC.remove(MeetingLogContextFilter.MEETING_ID);
    }
}
