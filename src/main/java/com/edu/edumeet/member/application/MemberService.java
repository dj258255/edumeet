package com.edu.edumeet.member.application;

import com.edu.edumeet.member.presentation.dto.request.LoginRequestDto;
import com.edu.edumeet.member.presentation.dto.request.RefreshTokenRequest;
import com.edu.edumeet.member.presentation.dto.request.SignupRequestDto;
import com.edu.edumeet.member.presentation.dto.response.TokenResponseDto;

public interface MemberService {
    void signup(SignupRequestDto signupRequestDto);
    TokenResponseDto login(LoginRequestDto loginRequest);
    TokenResponseDto refreshAccessToken(RefreshTokenRequest request);
    void logout(Long memberId);
}
