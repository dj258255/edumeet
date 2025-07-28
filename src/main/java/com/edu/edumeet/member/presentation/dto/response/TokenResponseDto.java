package com.edu.edumeet.member.presentation.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
public class TokenResponseDto {
    private String email;
    private String accessToken;
    private String refreshToken;

    @Builder
    private TokenResponseDto(String email, String accessToken, String refreshToken) {
        this.email = email;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public static TokenResponseDto from(String email, String accessToken, String refreshToken) {
        return TokenResponseDto.builder()
                .email(email)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }
}
