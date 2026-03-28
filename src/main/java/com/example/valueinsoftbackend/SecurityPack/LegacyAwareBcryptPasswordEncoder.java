package com.example.valueinsoftbackend.SecurityPack;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public class LegacyAwareBcryptPasswordEncoder implements PasswordEncoder {

    private final BCryptPasswordEncoder delegate = new BCryptPasswordEncoder();

    @Override
    public String encode(CharSequence rawPassword) {
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            return false;
        }

        if (isBcryptHash(encodedPassword)) {
            return delegate.matches(rawPassword, encodedPassword);
        }

        return encodedPassword.contentEquals(rawPassword);
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return !isBcryptHash(encodedPassword);
    }

    private boolean isBcryptHash(String encodedPassword) {
        return encodedPassword.startsWith("$2a$")
                || encodedPassword.startsWith("$2b$")
                || encodedPassword.startsWith("$2y$");
    }
}
