package com.example.valueinsoftbackend.security;

import com.example.valueinsoftbackend.SecurityPack.LegacyAwareBcryptPasswordEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-4 regression: the encoder must accept ONLY bcrypt-hashed passwords. The legacy
 * plaintext-compatibility branch is removed, so a plaintext value stored at rest can never
 * authenticate. (Existing plaintext rows are re-hashed to bcrypt by Flyway V138.)
 */
class LegacyAwareBcryptPasswordEncoderTest {

    private LegacyAwareBcryptPasswordEncoder encoder;
    private BCryptPasswordEncoder rawBcrypt;

    @BeforeEach
    void setUp() {
        encoder = new LegacyAwareBcryptPasswordEncoder();
        rawBcrypt = new BCryptPasswordEncoder();
    }

    @Test
    void matches_returnsTrue_forCorrectBcryptPassword() {
        String stored = encoder.encode("correct horse battery staple");
        assertTrue(encoder.matches("correct horse battery staple", stored));
    }

    @Test
    void matches_verifies_bcryptHashGeneratedElsewhere() {
        // A '$2a$' hash produced independently (e.g. by Postgres pgcrypto in V138) must verify.
        String stored = rawBcrypt.encode("s3cret");
        assertTrue(encoder.matches("s3cret", stored));
        assertFalse(encoder.matches("wrong", stored));
    }

    @Test
    void matches_returnsFalse_forPlaintextStoredValue() {
        // The core P1-4 fix: a plaintext stored value must NOT authenticate even if it equals
        // the raw password (previously this returned true).
        assertFalse(encoder.matches("myPassword", "myPassword"));
    }

    @Test
    void matches_returnsFalse_forLockedOrNonBcryptSentinel() {
        assertFalse(encoder.matches("anything", "!LOCKED_PENDING_PASSWORD_RESET!"));
        assertFalse(encoder.matches("anything", "md5oldhashformat"));
    }

    @Test
    void matches_returnsFalse_forNullOrBlankStoredValue() {
        assertFalse(encoder.matches("x", null));
        assertFalse(encoder.matches("x", ""));
        assertFalse(encoder.matches("x", "   "));
    }

    @Test
    void upgradeEncoding_isFalse_soNoSilentRehashOfNonBcrypt() {
        assertFalse(encoder.upgradeEncoding(encoder.encode("x")));
        assertFalse(encoder.upgradeEncoding("plaintext"));
    }
}
