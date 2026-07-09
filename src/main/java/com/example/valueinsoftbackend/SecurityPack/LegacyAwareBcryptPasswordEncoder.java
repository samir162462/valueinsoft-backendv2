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

        // P1-4: only bcrypt-hashed passwords are accepted. The legacy plaintext-compatibility
        // branch has been removed so that a plaintext value stored at rest can NEVER authenticate.
        // Any pre-existing plaintext passwords are migrated to bcrypt in place by Flyway migration
        // V138 (which also flags those accounts with password_reset_required = TRUE).
        if (isBcryptHash(encodedPassword)) {
            return delegate.matches(rawPassword, encodedPassword);
        }

        return false;
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        // Post-migration all stored passwords are bcrypt; a non-bcrypt value is rejected by
        // matches() rather than being silently accepted and re-hashed. Nothing to upgrade.
        return false;
    }

    private boolean isBcryptHash(String encodedPassword) {
        return encodedPassword.startsWith("$2a$")
                || encodedPassword.startsWith("$2b$")
                || encodedPassword.startsWith("$2y$");
    }
}
