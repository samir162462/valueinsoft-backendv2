package com.example.valueinsoftbackend.notification.service;

import com.example.valueinsoftbackend.notification.config.NotificationCipherProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Service
public class NotificationTokenCipher {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final NotificationCipherProperties properties;
    private final SecureRandom random = new SecureRandom();

    public NotificationTokenCipher(NotificationCipherProperties properties) {
        this.properties = properties;
    }

    public EncryptedToken encrypt(String token) {
        requireToken(token);
        String keyId = properties.getActiveKeyId();
        byte[] key = decodeKey(keyId);
        byte[] iv = new byte[properties.getIvLengthBytes()];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(properties.getTagLengthBits(), iv));
            cipher.updateAAD(keyId.getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return new EncryptedToken(
                    keyId,
                    ByteBuffer.allocate(iv.length + ciphertext.length)
                            .put(iv)
                            .put(ciphertext)
                            .array(),
                    hash(token));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Push credential encryption failed", exception);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    public String decrypt(byte[] encrypted, String keyId) {
        if (encrypted == null || encrypted.length <= properties.getIvLengthBytes()) {
            throw new IllegalArgumentException("Encrypted push credential is invalid");
        }
        byte[] key = decodeKey(keyId);
        byte[] iv = Arrays.copyOfRange(encrypted, 0, properties.getIvLengthBytes());
        byte[] ciphertext = Arrays.copyOfRange(
                encrypted, properties.getIvLengthBytes(), encrypted.length);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(properties.getTagLengthBits(), iv));
            cipher.updateAAD(keyId.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Push credential decryption failed", exception);
        } finally {
            Arrays.fill(key, (byte) 0);
            Arrays.fill(ciphertext, (byte) 0);
        }
    }

    public byte[] hash(String token) {
        requireToken(token);
        if (properties.getTokenHashPepper() == null
                || properties.getTokenHashPepper().isBlank()) {
            throw new IllegalStateException("Push credential hash pepper is not configured");
        }
        byte[] pepper;
        try {
            pepper = Base64.getDecoder().decode(properties.getTokenHashPepper());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Push credential hash pepper is not valid base64",
                    exception);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(pepper);
            return digest.digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        } finally {
            Arrays.fill(pepper, (byte) 0);
        }
    }

    private byte[] decodeKey(String keyId) {
        String encoded = properties.getKeys().get(keyId);
        if (keyId == null || keyId.isBlank() || encoded == null || encoded.isBlank()) {
            throw new IllegalStateException("Active push credential key is not configured");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Push credential key " + keyId + " is not valid base64", exception);
        }
        if (key.length != 32) {
            Arrays.fill(key, (byte) 0);
            throw new IllegalStateException("Push credential key must contain exactly 32 bytes");
        }
        return key;
    }

    private static void requireToken(String token) {
        if (token == null || token.isBlank() || token.length() > 4096) {
            throw new IllegalArgumentException("Push credential must contain 1 to 4096 characters");
        }
    }

    public record EncryptedToken(String keyId, byte[] encrypted, byte[] hash) {
    }
}
