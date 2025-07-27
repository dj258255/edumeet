package com.edu.edumeet.member.application;

import com.edu.edumeet.member.presentation.dto.request.SignupRequestDto;

public interface MemberService {
    void signup(SignupRequestDto signupRequestDto);
}
