package com.edu.edumeet.member.service;

import com.edu.edumeet.config.jwt.JwtService;
import com.edu.edumeet.email.presentation.dto.request.EmailRequest;
import com.edu.edumeet.member.repository.MemberRepository;
import com.edu.edumeet.member.repository.RefreshTokenRepository;
import com.edu.edumeet.member.domain.Member;
import com.edu.edumeet.member.domain.RefreshToken;
import com.edu.edumeet.member.dto.request.LoginRequestDto;
import com.edu.edumeet.member.dto.request.RefreshTokenRequest;
import com.edu.edumeet.member.dto.request.SignupRequestDto;
import com.edu.edumeet.member.dto.response.SignupResponseDto;
import com.edu.edumeet.member.dto.response.TokenResponseDto;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public void signup(SignupRequestDto signupRequestDto) {
        String email = signupRequestDto.getEmail();
        String nickname = signupRequestDto.getNickname();
        String rawPassword = signupRequestDto.getPassword();

        validateIsExistsMember(email);

        Member member = Member.create(email, rawPassword, nickname, passwordEncoder);
        Member savedMember = memberRepository.save(member);
        
        log.info("회원가입 완료 - 생성된 id: {}, email: {}", savedMember.getId(), email);
    }

    public TokenResponseDto login(LoginRequestDto loginRequest) {
        String email = loginRequest.getEmail();
        String password = loginRequest.getPassword();

        log.info("로그인 시도: {}", email);

        authenticate(email, password);

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        if (member.getId() == null) {
            log.error("조회된 Member의 ID가 null입니다! - email: {}", email);
            throw new RuntimeException("회원 데이터 오류: ID가 없습니다. 관리자에게 문의하세요.");
        }

        log.info("회원 정보 조회 성공 - memberId: {}, email: {}", member.getId(), member.getEmail());

        String accessToken = jwtService.generateAccessToken(member.getId(), member.getEmail());
        String refreshTokenValue = jwtService.generateRefreshToken(member.getId(), member.getEmail());

        log.info("JWT 토큰 생성 완료 - memberId: {}", member.getId());

        LocalDateTime refreshTokenExpiration = LocalDateTime.now()
                .plusSeconds(jwtService.getRefreshTokenExpTime() / 1000);

        RefreshToken refreshToken = RefreshToken.create(
                member.getId(), 
                refreshTokenValue, 
                refreshTokenExpiration
        );

        log.info("RefreshToken 엔티티 생성 완료 - memberId: {}, expiration: {}",
                member.getId(), refreshTokenExpiration);

        try {
            log.info("RefreshToken 저장 시작 - memberId: {}", member.getId());
            saveOrUpdateRefreshToken(member.getId(), refreshToken);
            log.info("RefreshToken 저장 성공 - memberId: {}", member.getId());
        } catch (Exception e) {
            log.error("RefreshToken 저장 실패 - memberId: {}, member 객체: {}",
                    member.getId(), member, e);
            throw new RuntimeException("로그인 처리 중 오류가 발생했습니다.", e);
        }

        log.info("로그인 성공: {}", email);

        return TokenResponseDto.builder()
                .email(member.getEmail())
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .build();
    }

    public TokenResponseDto refreshAccessToken(RefreshTokenRequest request) {
        String refreshTokenValue = request.getRefreshToken();

        log.info("토큰 재발급 요청");

        if (!jwtService.isTokenValid(refreshTokenValue)) {
            throw new IllegalArgumentException("유효하지 않은 RefreshToken입니다.");
        }

        Long memberId = jwtService.getMemberIdFromToken(refreshTokenValue);
        String email = jwtService.extractUsername(refreshTokenValue);

        log.info("토큰에서 추출한 정보 - memberId: {}, email: {}", memberId, email);

        RefreshToken storedRefreshToken = refreshTokenRepository.findByMemberId(memberId)
                .orElseThrow(() -> new IllegalArgumentException("저장된 RefreshToken을 찾을 수 없습니다."));

        if (!storedRefreshToken.getToken().equals(refreshTokenValue)) {
            throw new IllegalArgumentException("유효하지 않은 RefreshToken입니다.");
        }

        if (storedRefreshToken.isExpired(LocalDateTime.now())) {
            refreshTokenRepository.deleteByMemberId(memberId);
            throw new IllegalArgumentException("만료된 RefreshToken입니다.");
        }

        String newAccessToken = jwtService.generateAccessToken(memberId, email);

        log.info("토큰 재발급 성공: {}", email);

        return TokenResponseDto.builder()
                .email(email)
                .accessToken(newAccessToken)
                .refreshToken(refreshTokenValue)
                .build();
    }

    public void logout(Long memberId) {
        log.info("로그아웃 요청: memberId={}", memberId);
        refreshTokenRepository.deleteByMemberId(memberId);
        log.info("로그아웃 완료: memberId={}", memberId);
    }

    public void emailCheck(EmailRequest emailRequest) {
        validateIsExistsMember(emailRequest.getEmail());
    }

    public List<SignupResponseDto> searchByEmail(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("검색 키워드는 비어있을 수 없습니다.");
        }

        String trimmedKeyword = keyword.trim();
        if (trimmedKeyword.length() < 2) {
            throw new IllegalArgumentException("검색 키워드는 최소 2자 이상이어야 합니다.");
        }

        log.info("회원 검색 요청 - keyword: {}", trimmedKeyword);

        List<Member> members = memberRepository.findByEmailContainingIgnoreCase(trimmedKeyword);

        log.info("검색 결과 - 총 {}명의 회원을 찾았습니다.", members.size());

        return members.stream()
                .map(member -> SignupResponseDto.builder()
                        .email(member.getEmail())
                        .nickname(member.getNickname())
                        .build())
                .toList();
    }

    private void validateIsExistsMember(String email) {
        if (memberRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("이미 존재하는 회원입니다.");
        }
    }

    private void authenticate(String email, String password) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
            );
        } catch (DisabledException e) {
            log.warn("비활성화된 계정 로그인 시도: {}", email);
            throw new IllegalArgumentException("비활성화된 계정입니다.");
        } catch (BadCredentialsException e) {
            log.warn("잘못된 인증 정보 로그인 시도: {}", email);
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        } catch (Exception e) {
            log.error("인증 과정에서 예외 발생: {}", email, e);
            throw new IllegalArgumentException("로그인 중 오류가 발생했습니다.");
        }
    }

    private void saveOrUpdateRefreshToken(Long memberId, RefreshToken refreshToken) {
        if (refreshTokenRepository.existsByMemberId(memberId)) {
            RefreshToken existingToken = refreshTokenRepository.findByMemberId(memberId)
                    .orElseThrow(() -> new RuntimeException("RefreshToken 조회 실패"));
            existingToken.updateToken(refreshToken.getToken(), refreshToken.getExpiration());
            refreshTokenRepository.save(existingToken);
        } else {
            refreshTokenRepository.save(refreshToken);
        }
    }
}
