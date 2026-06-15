package com.example.valueinsoftbackend.whatsapp.service;

import com.example.valueinsoftbackend.whatsapp.config.WhatsAppProperties;
import com.example.valueinsoftbackend.whatsapp.exception.WhatsAppException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
@Slf4j
public class WhatsAppTokenEncryptionService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final WhatsAppProperties properties;
    private SecretKeySpec secretKey;

    public WhatsAppTokenEncryptionService(WhatsAppProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        String key = properties.getEncryptionKey();
        if (key != null && key.length() >= 32) {
            byte[] keyBytes = key.substring(0, 32).getBytes(StandardCharsets.UTF_8);
            this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
        } else {
            // Fallback for dev if not configured properly, though it's logged in properties
            byte[] keyBytes = "fallback_dev_key_32_bytes_length!".getBytes(StandardCharsets.UTF_8);
            this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
            
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            
            // Combine IV and ciphertext
            byte[] ivAndCipherText = new byte[GCM_IV_LENGTH + cipherText.length];
            System.arraycopy(iv, 0, ivAndCipherText, 0, GCM_IV_LENGTH);
            System.arraycopy(cipherText, 0, ivAndCipherText, GCM_IV_LENGTH, cipherText.length);
            
            return Base64.getEncoder().encodeToString(ivAndCipherText);
        } catch (Exception e) {
            log.error("Failed to encrypt token", e);
            throw new WhatsAppException(HttpStatus.INTERNAL_SERVER_ERROR, "ENCRYPTION_ERROR", "INTERNAL", "Failed to encrypt access token");
        }
    }

    public String decrypt(String encryptedText) {
        if (encryptedText == null) return null;
        try {
            byte[] ivAndCipherText = Base64.getDecoder().decode(encryptedText);
            
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(ivAndCipherText, 0, iv, 0, GCM_IV_LENGTH);
            
            int cipherTextLength = ivAndCipherText.length - GCM_IV_LENGTH;
            byte[] cipherText = new byte[cipherTextLength];
            System.arraycopy(ivAndCipherText, GCM_IV_LENGTH, cipherText, 0, cipherTextLength);
            
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
            
            byte[] plaintext = cipher.doFinal(cipherText);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to decrypt token", e);
            throw new WhatsAppException(HttpStatus.INTERNAL_SERVER_ERROR, "DECRYPTION_ERROR", "INTERNAL", "Failed to decrypt access token");
        }
    }
}
