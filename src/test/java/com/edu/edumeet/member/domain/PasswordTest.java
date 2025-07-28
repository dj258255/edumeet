package com.edu.edumeet.member.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.Mockito.verify;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("[비밀번호 테스트]")
public class PasswordTest {
    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void of_정상_생성_테스트() {
        String encoded = "PASSWORD";
        Password password = Password.of(encoded);

        assertEquals(encoded, password.getEncoded());
    }

    @Test
    void encode_정상적으로_인코딩된다() {
        String raw = "raw_password";
        when(passwordEncoder.encode(raw)).thenReturn("encoded_password");

        Password encoded = Password.encode(raw, passwordEncoder);

        assertThat(encoded.getEncoded()).isEqualTo("encoded_password");
    }

    @Test
    void matches_같은비밀번호면_true반환() {
        // given
        String raw = "plain_pw";
        String hashed = "hashed_pw";

        Password password = Password.of(hashed);
        given(passwordEncoder.matches(raw, hashed)).willReturn(true);

        // when
        boolean result = password.matches(raw, passwordEncoder);

        // then
        assertThat(result).isTrue();
        verify(passwordEncoder).matches(raw, hashed);
    }

    @Test
    void matches_다른비밀번호면_false반환() {
        // given
        String raw = "wrong_pw";
        String hashed = "hashed_pw";

        Password password = Password.of(hashed);
        given(passwordEncoder.matches(raw, hashed)).willReturn(false);

        // when
        boolean result = password.matches(raw, passwordEncoder);

        // then
        assertThat(result).isFalse();
        verify(passwordEncoder).matches(raw, hashed);
    }
}
