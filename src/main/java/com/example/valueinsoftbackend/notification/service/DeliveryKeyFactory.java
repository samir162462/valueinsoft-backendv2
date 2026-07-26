package com.example.valueinsoftbackend.notification.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
public class DeliveryKeyFactory {
    public byte[] create(long companyId,
                         long eventId,
                         int userId,
                         long deviceId,
                         String channel,
                         int payloadVersion) {
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("Delivery channel is required");
        }
        String material = companyId + "|" + eventId + "|" + userId + "|"
                + deviceId + "|" + channel + "|" + payloadVersion;
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
