package com.edu.edumeet.config.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 서버 간 호출 인증. (#27)
 *
 * <p>파이썬 AI 서버는 사용자 JWT 를 가질 수 없다. 사람이 로그인해서 부르는 게 아니기 때문이다.
 * #23 이전에는 {@code /api/v1/**} 이 permitAll 이라 이 경로가 <b>인증 없이 열려 있었고</b>,
 * 누구나 임의 클래스에 요약본을 덮어쓸 수 있었다.
 *
 * <p>공유 시크릿을 쓴다. 서버 간 호출이라 사용자 신원이 없고, 상호 TLS 를 붙일 만한
 * 인프라도 아니다. 대신 <b>실패 시 닫힌다</b> — 토큰이 설정되지 않으면 아무도 통과하지 못한다.
 *
 * <p>한계: 회전 절차가 없다. 유출되면 환경변수를 바꾸고 재배포해야 한다.
 * 호출 빈도가 낮고(회의당 1회) 내부 네트워크 호출이라 감수한다.
 */
@Slf4j
@Component
public class InternalApiTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";
    private static final String PATH_PREFIX = "/api/v1/internal/";

    private final byte[] expected;

    public InternalApiTokenFilter(@Value("${edumeet.internal.api-token:}") String token) {
        this.expected = (token == null || token.isBlank())
                ? null
                : token.getBytes(StandardCharsets.UTF_8);
        if (this.expected == null) {
            log.warn("edumeet.internal.api-token 이 비어 있다. {} 경로는 전부 거부된다.", PATH_PREFIX);
        }
    }

    /** 내부 경로가 아니면 이 필터는 아무것도 하지 않는다. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (matches(request.getHeader(HEADER))) {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            "internal-service", null,
                            AuthorityUtils.createAuthorityList("ROLE_INTERNAL")));
        } else {
            // 인증을 설정하지 않고 넘긴다. 인가 단계에서 401 이 나간다.
            // 여기서 직접 응답을 쓰면 EntryPoint 의 응답 형식과 어긋난다.
            log.warn("내부 API 토큰 불일치 - uri={}, remote={}",
                    request.getRequestURI(), request.getRemoteAddr());
        }
        chain.doFilter(request, response);
    }

    /**
     * 상수 시간 비교. 바이트별로 일찍 끊으면 응답 시간으로 토큰을 한 글자씩 맞출 수 있다.
     * 길이는 여전히 새지만 그것만으로는 값을 복원할 수 없다.
     */
    private boolean matches(String presented) {
        if (expected == null || presented == null) {
            return false;
        }
        return MessageDigest.isEqual(expected, presented.getBytes(StandardCharsets.UTF_8));
    }
}
