package com.edu.edumeet.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@Slf4j
public class OAuth2DebugController {

    @Autowired(required = false)
    private ClientRegistrationRepository clientRegistrationRepository;

    @GetMapping("/debug/oauth2")
    public Map<String, Object> debugOAuth2() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (clientRegistrationRepository == null) {
                result.put("error", "ClientRegistrationRepository가 null입니다.");
                log.error("ClientRegistrationRepository가 null입니다.");
                return result;
            }

            ClientRegistration kakaoRegistration = clientRegistrationRepository.findByRegistrationId("kakao");
            if (kakaoRegistration == null) {
                result.put("error", "카카오 ClientRegistration이 없습니다.");
                log.error("카카오 ClientRegistration이 없습니다.");
                return result;
            }

            result.put("success", true);
            result.put("clientId", kakaoRegistration.getClientId());
            result.put("redirectUri", kakaoRegistration.getRedirectUri());
            result.put("authorizationUri", kakaoRegistration.getProviderDetails().getAuthorizationUri());
            result.put("tokenUri", kakaoRegistration.getProviderDetails().getTokenUri());
            
            log.info("카카오 OAuth2 설정 확인됨:");
            log.info("- Client ID: {}", kakaoRegistration.getClientId());
            log.info("- Redirect URI: {}", kakaoRegistration.getRedirectUri());
            log.info("- Authorization URI: {}", kakaoRegistration.getProviderDetails().getAuthorizationUri());

        } catch (Exception e) {
            result.put("error", "OAuth2 설정 확인 중 오류: " + e.getMessage());
            log.error("OAuth2 설정 확인 중 오류:", e);
        }

        return result;
    }
}
