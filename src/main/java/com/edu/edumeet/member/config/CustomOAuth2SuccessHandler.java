package com.edu.edumeet.member.config;

import java.time.Duration;
import com.edu.edumeet.member.service.RefreshTokenStore;
import com.edu.edumeet.config.jwt.JwtService;
import com.edu.edumeet.member.domain.CustomOauth2UserDetails;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final RefreshTokenStore refreshTokenStore;
    
    @Value("${front.url2}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
    HttpServletResponse response,
    Authentication authentication) throws IOException, ServletException {

    log.info("=== OAuth2 성공 핸들러 시작 ===");
    
        try {
    CustomOauth2UserDetails userDetails = (CustomOauth2UserDetails) authentication.getPrincipal();
    log.info("OAuth2 인증 사용자 정보: {}", userDetails.getMember().getEmail());

    Long memberId = userDetails.getMember().getId();
    String email = userDetails.getMember().getEmail();
    
            log.info("JWT 토큰 생성 시작 - memberId: {}, email: {}", memberId, email);

            // JWT 생성
    String accessToken = jwtService.generateAccessToken(memberId, email);
    String refreshToken = jwtService.generateRefreshToken(memberId, email);

    // 카카오 로그인도 refresh token 을 저장한다. (#70)
    // 이전에는 생성만 하고 저장하지 않아서, OAuth 로 로그인한 사용자는
    // 토큰 갱신 요청이 "저장된 RefreshToken 을 찾을 수 없습니다" 로 실패했다.
    refreshTokenStore.save(memberId, refreshToken,
            Duration.ofMillis(jwtService.getRefreshTokenExpTime()));

    log.info("✅ OAuth2 로그인 성공 - 사용자: {}, memberId: {}", email, memberId);
            log.info("✅ JWT 토큰 발급 완료 - accessToken 길이: {}, refreshToken 길이: {}", 
            accessToken.length(), refreshToken.length());

            // 개선된 세션 기반 토큰 저장
            request.getSession().setAttribute("oauth2_success", true);
            request.getSession().setAttribute("accessToken", accessToken);
            request.getSession().setAttribute("refreshToken", refreshToken);
            request.getSession().setAttribute("userEmail", email);
            request.getSession().setAttribute("userNickname", userDetails.getMember().getNickname());
            
            // 프론트엔드에서 별도 API로 토큰 조회하도록 리다이렉트
            String redirectUrl = frontendUrl + "/oauth2/success";
            
            log.info("프론트엔드로 리다이렉트: {}", redirectUrl);
            response.sendRedirect(redirectUrl);
            
        } catch (Exception e) {
            log.error("OAuth2 성공 처리 중 오류 발생", e);
            response.sendRedirect(frontendUrl + "/login?error=oauth_failed");
        }
        
        log.info("=== OAuth2 성공 핸들러 완료 ===");
    }
}