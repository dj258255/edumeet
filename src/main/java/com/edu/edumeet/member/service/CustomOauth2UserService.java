package com.edu.edumeet.member.service;

import com.edu.edumeet.member.domain.CustomOauth2UserDetails;
import com.edu.edumeet.member.domain.KakaoUserDetails;
import com.edu.edumeet.member.domain.Member;
import com.edu.edumeet.member.domain.OAuth2UserInfo;
import com.edu.edumeet.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOauth2UserService extends DefaultOAuth2UserService {
    private final MemberRepository memberRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        log.info("=== OAuth2 로그인 시작 ===");
        log.info("getAttributes : {}", oAuth2User.getAttributes());

        String provider = userRequest.getClientRegistration().getRegistrationId();
        log.info("OAuth2 provider: {}", provider);

        OAuth2UserInfo oAuth2UserInfo = null;

        if(provider.equals("kakao")){
            log.info("카카오 로그인 인식");
            oAuth2UserInfo = new KakaoUserDetails(oAuth2User.getAttributes());
        } else {
            log.error("지원하지 않는 OAuth2 provider: {}", provider);
            OAuth2Error error = new OAuth2Error("unsupported_provider", "지원하지 않는 OAuth2 제공자입니다: " + provider, null);
            throw new OAuth2AuthenticationException(error);
        }

        String providerId = oAuth2UserInfo.getProviderId();
        String email = oAuth2UserInfo.getEmail(); // 카카오에서 제공하는 실제 이메일
        String nickname = oAuth2UserInfo.getName();
        
        // 🔥 이메일 유효성 검증
        if (email == null || email.trim().isEmpty()) {
            log.error("OAuth2 제공자로부터 이메일을 받지 못했습니다. provider: {}, providerId: {}", provider, providerId);
            OAuth2Error error = new OAuth2Error("email_not_provided", "OAuth2 제공자로부터 이메일을 받지 못했습니다.", null);
            throw new OAuth2AuthenticationException(error);
        }
        
        log.info("추출된 사용자 정보 - providerId: {}, email: {}, nickname: {}", 
                providerId, email, nickname);

        // 🔥 변경된 부분: 실제 이메일로 사용자 조회
        Member member = memberRepository.findByEmail(email)
                .map(existingMember -> {
                    log.info("✅ 기존 사용자 로그인 (이메일 기준): email={}, memberId={}, nickname={}", 
                            email, existingMember.getId(), existingMember.getNickname());
                    
                    // 🔥 기존 사용자의 OAuth2 정보 업데이트 (provider, providerId가 없는 경우)
                    if (existingMember.getProvider() == null || existingMember.getProviderId() == null) {
                        log.info("기존 일반 회원의 OAuth2 정보 업데이트: email={}", email);
                        existingMember.updateOAuth2Info(provider, providerId);
                        memberRepository.save(existingMember);
                    }
                    
                    // 닉네임이 변경되었을 수 있으므로 업데이트 가능
                    if (!existingMember.getNickname().equals(nickname)) {
                        log.info("닉네임 업데이트: {} -> {}", existingMember.getNickname(), nickname);
                        existingMember.updateNickname(nickname);
                        memberRepository.save(existingMember);
                    }
                    
                    return existingMember;
                })
                .orElseGet(() -> {
                    // 🔥 providerId로 중복 체크 (같은 카카오 계정이 다른 이메일로 가입한 경우 방지)
                    return memberRepository.findByProviderAndProviderId(provider, providerId)
                            .map(existingMember -> {
                                log.warn("⚠️ 동일한 providerId로 가입된 사용자가 다른 이메일을 사용합니다. " +
                                        "기존 이메일: {}, 새 이메일: {}", existingMember.getEmail(), email);
                                // 이메일이 변경된 경우 업데이트
                                if (!existingMember.getEmail().equals(email)) {
                                    existingMember.updateEmail(email);
                                    memberRepository.save(existingMember);
                                }
                                return existingMember;
                            })
                            .orElseGet(() -> {
                                log.info("신규 OAuth2 사용자 생성 시작: email={}, nickname={}", email, nickname);
                                
                                try {
                                    Member newMember = Member.builder()
                                            .email(email) // 🔥 실제 카카오 이메일 저장
                                            .password("OAUTH2_USER_NO_PASSWORD")
                                            .nickname(nickname)
                                            .provider(provider)
                                            .providerId(providerId)
                                            .build();
                                    
                                    Member savedMember = memberRepository.save(newMember);
                                    log.info("✅ 신규 OAuth2 사용자 저장 성공: memberId={}, email={}", 
                                            savedMember.getId(), savedMember.getEmail());
                                    
                                    return savedMember;
                                } catch (Exception e) {
                                    log.error("신규 OAuth2 사용자 생성 실패: {}", e.getMessage(), e);
                                    OAuth2Error error = new OAuth2Error("user_creation_failed", "사용자 생성에 실패했습니다: " + e.getMessage(), null);
                                    throw new OAuth2AuthenticationException(error, e);
                                }
                            });
                });

        log.info("✅ OAuth2 사용자 처리 완료: memberId={}, email={}", member.getId(), member.getEmail());
        log.info("=== OAuth2 로그인 완료 ===");

        return new CustomOauth2UserDetails(member, oAuth2User.getAttributes());
    }
}
