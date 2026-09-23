package com.edu.edumeet.config.jwt;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authorization 헤더의 JWT 로 인증을 채운다.
 *
 * <p><b>토큰 오류를 삼키지도, 500 으로 만들지도 않는다.</b> (#209)
 *
 * <pre>
 *   삼키면   200 · 빈 본문   - 위조·만료 토큰에도 "성공" 처럼 보인다. 운영에서 그랬다
 *   올리면   500           - 위조·만료 토큰은 서버 오류가 아니다
 *   비우고 계속  401        - 보호된 경로는 보안 진입점이 거절한다. 이게 맞다
 * </pre>
 *
 * <p>그래서 <b>토큰 해석·검증만</b> 따로 감싸고, 실패하면 컨텍스트를 비운 뒤 인증 없이 체인을 계속한다.
 * {@code filterChain.doFilter} 는 감싸지 않는다 — 처리되지 않은 예외는 그대로 올라가
 * 컨테이너가 5xx 로 만든다. 예전에는 여기서 예외를 잡아 리졸버에 넘기고 반환값을 보지 않아서,
 * 리졸버가 처리하지 못한 예외가 <b>아무 흔적도 남기지 않고</b> 사라졌다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        //스웨거 경로도 추가
        if(path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/api-docs")        // springdoc.api-docs.path=/api-docs 설정 대응
                || path.startsWith("/swagger-resources")
                || path.startsWith("/webjars")) {
            filterChain.doFilter(request, response);
            return;
        }
        //-------------------------------

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // ★ 토큰 해석·검증만 감싼다. 위조·만료·형식 오류는 "인증되지 않음" 이다.
        try {
            authenticate(request, authHeader.substring(7));
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException e) {
            // 토큰 오류와 "그 토큰의 사용자가 없다" 만 여기서 받는다.
            // 넓게(Exception) 잡으면 loadUserByUsername 의 DB 장애까지 401 로 위장되어
            // 클라이언트가 토큰 갱신·로그아웃으로 빠진다 - 인프라 실패는 그대로 올려 5xx 로 드러낸다.
            SecurityContextHolder.clearContext();
            log.debug("JWT 인증 실패 - 인증 없이 계속한다: {}", e.getMessage());
        }

        // ★ 감싸지 않는다. 처리되지 않은 예외는 그대로 올라가 5xx 가 되고 로그에 남는다.
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String jwt) {
        final String userEmail = jwtService.extractUsername(jwt);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (userEmail != null && authentication == null) {
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

            if (jwtService.isTokenValid(jwt, userDetails)) {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
    }
}
