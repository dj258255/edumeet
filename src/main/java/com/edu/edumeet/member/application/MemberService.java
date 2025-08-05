package com.edu.edumeet.member.application;

import com.edu.edumeet.email.presentation.dto.request.EmailRequest;
import com.edu.edumeet.member.presentation.dto.request.LoginRequestDto;
import com.edu.edumeet.member.presentation.dto.request.RefreshTokenRequest;
import com.edu.edumeet.member.presentation.dto.request.SignupRequestDto;
import com.edu.edumeet.member.presentation.dto.response.SignupResponseDto;
import com.edu.edumeet.member.presentation.dto.response.TokenResponseDto;

import java.util.List;

public interface MemberService {
    void signup(SignupRequestDto signupRequestDto);
    TokenResponseDto login(LoginRequestDto loginRequest);
    TokenResponseDto refreshAccessToken(RefreshTokenRequest request);
    void logout(Long memberId);
    void emailCheck(EmailRequest emailRequest);
    List<SignupResponseDto> searchByEmail(String keyword);
}
