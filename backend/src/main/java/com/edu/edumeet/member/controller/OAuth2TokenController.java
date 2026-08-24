package com.edu.edumeet.member.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/oauth2")
@Component
@Slf4j
public class OAuth2TokenController {
    
    public OAuth2TokenController() {
        log.info("🔥 OAuth2TokenController 로딩됨!");
    }
    
    @GetMapping("/token")
    public ResponseEntity<?> getTokenFromSession(HttpServletRequest request) {
        log.info("=== OAuth2 토큰 조회 요청 ===");
        
        try {
            Boolean success = (Boolean) request.getSession().getAttribute("oauth2_success");
            log.info("OAuth2 성공 플래그: {}", success);
            
            if (success == null || !success) {
                log.warn("OAuth2 인증이 완료되지 않았거나 세션이 없습니다.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "OAuth2 인증이 완료되지 않았습니다."));
            }
            
            String accessToken = (String) request.getSession().getAttribute("accessToken");
            String refreshToken = (String) request.getSession().getAttribute("refreshToken");
            String userEmail = (String) request.getSession().getAttribute("userEmail");
            String userNickname = (String) request.getSession().getAttribute("userNickname");
            
            log.info("세션에서 토큰 조회 성공 - 사용자: {}", userEmail);
            
            if (accessToken == null || refreshToken == null) {
                log.error("세션에 토큰이 없습니다.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "토큰을 찾을 수 없습니다."));
            }
            
            // 세션에서 토큰 제거 (보안상 한 번만 조회 가능)
            request.getSession().removeAttribute("oauth2_success");
            request.getSession().removeAttribute("accessToken");
            request.getSession().removeAttribute("refreshToken");
            request.getSession().removeAttribute("userEmail");
            request.getSession().removeAttribute("userNickname");
            
            Map<String, Object> response = new HashMap<>();
            response.put("accessToken", accessToken);
            response.put("refreshToken", refreshToken);
            response.put("userEmail", userEmail);
            response.put("userNickname", userNickname);
            
            log.info("✅ OAuth2 토큰 조회 완료 및 세션 정리됨");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("OAuth2 토큰 조회 중 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "토큰 조회 중 오류가 발생했습니다."));
        }
    }
    
    @GetMapping("/status")
    public ResponseEntity<?> getOAuth2Status(HttpServletRequest request) {
        log.info("=== OAuth2 상태 확인 요청 ===");
        
        Boolean success = (Boolean) request.getSession().getAttribute("oauth2_success");
        String userEmail = (String) request.getSession().getAttribute("userEmail");
        
        Map<String, Object> status = new HashMap<>();
        status.put("isAuthenticated", success != null && success);
        status.put("userEmail", userEmail);
        status.put("sessionId", request.getSession().getId());
        
        log.info("OAuth2 상태: {}", status);
        return ResponseEntity.ok(status);
    }
}
