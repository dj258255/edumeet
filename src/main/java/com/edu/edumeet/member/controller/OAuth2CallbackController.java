package com.edu.edumeet.member.controller;

import com.edu.edumeet.config.jwt.JwtService;
import com.edu.edumeet.member.domain.Member;
import com.edu.edumeet.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/oauth2")
@RequiredArgsConstructor
@Slf4j
public class OAuth2CallbackController {

    private final JwtService jwtService;
    private final MemberRepository memberRepository;

    @Value("${spring.security.oauth2.client.registration.kakao.client-id}")
    private String kakaoClientId;

    @Value("${spring.security.oauth2.client.registration.kakao.client-secret}")
    private String kakaoClientSecret;

    @Value("${kakao.redirect-uri}")
    private String kakaoRedirectUri;

    @PostMapping("/kakao/callback")
    public ResponseEntity<Map<String, Object>> handleKakaoCallback(@RequestBody Map<String, String> request) {
        try {
            String authorizationCode = request.get("code");
            log.info("카카오 인증 코드 수신: {}", authorizationCode);

            // 1. 카카오에서 액세스 토큰 요청
            String kakaoAccessToken = getKakaoAccessToken(authorizationCode);
            log.info("카카오 액세스 토큰 획득 성공");

            // 2. 카카오에서 사용자 정보 조회
            Map<String, Object> kakaoUserInfo = getKakaoUserInfo(kakaoAccessToken);
            log.info("카카오 사용자 정보 조회 성공: {}", kakaoUserInfo);

            // 3. 사용자 정보 추출
            String providerId = kakaoUserInfo.get("id").toString();
            
            Map<String, Object> kakaoAccount = (Map<String, Object>) kakaoUserInfo.get("kakao_account");
            Map<String, Object> properties = (Map<String, Object>) kakaoUserInfo.get("properties");

            String email = kakaoAccount != null ? (String) kakaoAccount.get("email") : null;
            String nickname = properties != null ? (String) properties.get("nickname") : "카카오사용자";

            // 4. 사용자 조회 또는 생성
            Member member = findOrCreateMember(email, nickname, providerId);

            // 5. JWT 토큰 생성
            String accessToken = jwtService.generateAccessToken(member.getId(), member.getEmail());
            String refreshToken = jwtService.generateRefreshToken(member.getId(), member.getEmail());

            log.info("✅ 카카오 OAuth2 로그인 성공 - 사용자: {}, memberId: {}", member.getEmail(), member.getId());

            // 응답 생성
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("email", member.getEmail());
            userMap.put("nickname", member.getNickname());
            userMap.put("provider", "kakao");

            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("accessToken", accessToken);
            responseMap.put("refreshToken", refreshToken);
            responseMap.put("user", userMap);

            return ResponseEntity.ok(responseMap);

        } catch (Exception e) {
            log.error("카카오 OAuth2 콜백 처리 실패", e);
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("error", "로그인 처리에 실패했습니다: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMap);
        }
    }

    private String getKakaoAccessToken(String authorizationCode) {
        RestTemplate restTemplate = new RestTemplate();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", kakaoClientId);
        params.add("client_secret", kakaoClientSecret);
        params.add("redirect_uri", kakaoRedirectUri); // 프론트엔드 페이지
        params.add("code", authorizationCode);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "https://kauth.kakao.com/oauth/token", request, Map.class);

        Map<String, Object> responseBody = response.getBody();
        return (String) responseBody.get("access_token");
    }

    private Map<String, Object> getKakaoUserInfo(String accessToken) {
        RestTemplate restTemplate = new RestTemplate();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<String> request = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                "https://kapi.kakao.com/v2/user/me", HttpMethod.GET, request, Map.class);

        return response.getBody();
    }

    private Member findOrCreateMember(String email, String nickname, String providerId) {
        // 실제 이메일로 조회
        return memberRepository.findByEmail(email)
                .map(existingMember -> {
                    log.info("✅ 기존 사용자 로그인: email={}, memberId={}", email, existingMember.getId());
                    
                    // OAuth2 정보 업데이트 (provider, providerId가 없는 경우)
                    if (existingMember.getProvider() == null || existingMember.getProviderId() == null) {
                        log.info("기존 일반 회원의 OAuth2 정보 업데이트: email={}", email);
                        existingMember.updateOAuth2Info("kakao", providerId);
                        memberRepository.save(existingMember);
                    }
                    
                    return existingMember;
                })
                .orElseGet(() -> {
                    // providerId로 중복 체크
                    return memberRepository.findByProviderAndProviderId("kakao", providerId)
                            .orElseGet(() -> {
                                log.info("신규 카카오 사용자 생성: email={}, nickname={}", email, nickname);
                                
                                Member newMember = Member.builder()
                                        .email(email)  // 실제 카카오 이메일 저장
                                        .password("OAUTH2_USER_NO_PASSWORD")
                                        .nickname(nickname)
                                        .provider("kakao")
                                        .providerId(providerId)
                                        .build();
                                        
                                return memberRepository.save(newMember);
                            });
                });
    }
}
