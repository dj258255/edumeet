package com.edu.edumeet.config.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 로그에 요청 아이디와 회의 번호를 붙인다. (#166)
 *
 * <h3>왜 필요한가</h3>
 * 로그를 모아 놓아도 <b>거를 것이 없으면 못 쓴다.</b>
 * <i>"3번 회의에서 무슨 일이 있었나"</i> 를 물으려면 로그에 회의 번호가 있어야 한다.
 * 메시지 문자열에 박아 넣는 방법도 있지만 그러면 <b>찾을 때 정규식을 짜야 하고,</b>
 * 그 정규식은 메시지 형식이 조금만 바뀌어도 조용히 0건이 된다.
 *
 * <p>MDC 에 넣으면 JSON 로그의 최상위 필드가 되고, Loki 에서
 * {@code | json | meetingId="3"} 으로 걸린다.
 *
 * <h3>요청 아이디를 서버가 만드는 이유</h3>
 * 한 요청이 여러 줄의 로그를 남긴다. 그것을 묶을 것이 없으면
 * <b>동시 요청이 섞여 어느 줄이 어느 요청인지 모른다.</b>
 * 부하 상황에서는 특히 그렇다 - 로그가 가장 필요한 순간이 가장 섞이는 순간이다.
 *
 * <p>클라이언트가 {@code X-Request-Id} 를 보내면 그것을 쓴다.
 * 프론트·파이썬이 같은 값을 실어 보내면 <b>서비스 경계를 넘어 한 줄로 묶인다.</b>
 *
 * <h3>반드시 지운다</h3>
 * 톰캣은 스레드를 재사용한다. MDC 를 안 지우면 <b>다음 요청이 남의 회의 번호를 달고 찍힌다.</b>
 * 그러면 로그가 틀린 답을 주고, 그게 아무 답도 없는 것보다 나쁘다.
 */
@Component
@Order(1)
public class MeetingLogContextFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID = "requestId";
    private static final String MEETING_ID = "meetingId";
    private static final String HEADER = "X-Request-Id";

    /** {@code /api/v1/.../meetings/{id}/...} 에서 번호만 뽑는다. */
    private static final Pattern MEETING_PATH = Pattern.compile("/meetings/(\\d+)(?:/|$)");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().substring(0, 8);
        }
        MDC.put(REQUEST_ID, requestId);

        Matcher m = MEETING_PATH.matcher(request.getRequestURI());
        if (m.find()) {
            MDC.put(MEETING_ID, m.group(1));
        }

        // 클라이언트도 같은 값을 볼 수 있어야 문의가 들어왔을 때 로그를 찾는다.
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            // 톰캣이 스레드를 재사용한다. 안 지우면 다음 요청이 남의 값을 달고 찍힌다.
            MDC.remove(REQUEST_ID);
            MDC.remove(MEETING_ID);
        }
    }
}
