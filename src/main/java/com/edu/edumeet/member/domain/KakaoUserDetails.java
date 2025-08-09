package com.edu.edumeet.member.domain;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@AllArgsConstructor
public class KakaoUserDetails implements OAuth2UserInfo {
    private Map<String, Object> attributes;

    @Override
    public String getProvider() {
        return "kakao";
    }

    @Override
    public String getProviderId() {
        String providerId = attributes.get("id").toString();
        log.info("추출된 providerId: {}", providerId);
        return providerId;
    }

    @Override
    public String getEmail() {
        try {
            Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
            if (kakaoAccount != null) {
                String email = (String) kakaoAccount.get("email");
                log.info("추출된 email: {}", email);
                return email;
            } else {
                log.warn("kakao_account 정보가 없습니다.");
                return null;
            }
        } catch (Exception e) {
            log.error("이메일 추출 실패: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public String getName() {
        try {
            Map<String, Object> properties = (Map<String, Object>) attributes.get("properties");
            if (properties != null) {
                String nickname = (String) properties.get("nickname");
                log.info("추출된 nickname: {}", nickname);
                return nickname;
            } else {
                log.warn("properties 정보가 없습니다.");
                return "카카오사용자";
            }
        } catch (Exception e) {
            log.error("닉네임 추출 실패: {}", e.getMessage(), e);
            return "카카오사용자";
        }
    }
}
