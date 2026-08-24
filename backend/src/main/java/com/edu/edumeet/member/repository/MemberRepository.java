package com.edu.edumeet.member.repository;

import com.edu.edumeet.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    boolean existsByEmail(String email);
    Optional<Member> findByEmail(String email);
    List<Member> findByEmailContainingIgnoreCase(String keyword);
    List<Member> findByProviderIsNotNull(); // OAuth2 사용자 조회용
    
    // 🔥 추가된 메서드: provider와 providerId로 사용자 조회
    Optional<Member> findByProviderAndProviderId(String provider, String providerId);
    
    // 🔥 추가된 메서드: providerId로만 조회 (provider는 현재 kakao만 있으므로 생략 가능하지만 확장성 고려)
    Optional<Member> findByProviderId(String providerId);
    
    // 🔥 추가된 메서드: OAuth2 사용자 중에서 이메일로 조회
    Optional<Member> findByEmailAndProviderIsNotNull(String email);
}
