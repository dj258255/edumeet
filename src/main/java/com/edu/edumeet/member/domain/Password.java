package com.edu.edumeet.member.domain;

import lombok.Getter;
import org.springframework.security.crypto.password.PasswordEncoder;

@Getter
public class Password {
    private String encoded;

    protected Password() {}

    private Password(String encoded) {
        this.encoded = encoded;
    }

    public static Password of(String encoded) {
        return new Password(encoded);
    }

    public static Password encode(String raw, PasswordEncoder passwordEncoder) {
        return new Password(passwordEncoder.encode(raw));
    }

    public boolean matches(String raw, PasswordEncoder passwordEncoder) {
        return passwordEncoder.matches(raw, this.encoded);
    }
}
