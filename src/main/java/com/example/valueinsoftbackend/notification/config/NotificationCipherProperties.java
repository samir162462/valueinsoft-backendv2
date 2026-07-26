package com.example.valueinsoftbackend.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Push-token encryption material (NOTIFICATION_CENTER_PLAN.md §11.1).
 *
 * <p>Push tokens are credentials. They are stored AES-256-GCM encrypted in
 * {@code notification_device.push_token_enc} and are searchable only through
 * {@code token_hash = SHA-256(pepper || token)}.
 *
 * <p>{@link #keys} maps a key id to a base64-encoded 32-byte key. Decryption accepts every
 * key present, so rotation is: add the new key, set {@link #activeKeyId} to it, let the
 * re-wrap job migrate existing rows, then remove the old key. No downtime, no redeploy
 * beyond the environment change.
 *
 * <p>{@link #tokenHashPepper} lives only in configuration, never in the database — that is
 * what stops a database dump alone from confirming whether a known token is present.
 */
@Component
@ConfigurationProperties(prefix = "valueinsoft.notification.cipher")
@Getter
@Setter
public class NotificationCipherProperties {

    /** Key id used for all new encryptions. Must be present in {@link #keys}. */
    private String activeKeyId = "";

    /** keyId -> base64-encoded 32-byte AES key. Never logged, never exposed by any endpoint. */
    private Map<String, String> keys = new LinkedHashMap<>();

    /** Base64-encoded pepper mixed into the token hash. Configuration only. */
    private String tokenHashPepper = "";

    /** GCM IV length in bytes. 12 is the value NIST recommends for GCM. */
    private int ivLengthBytes = 12;

    /** GCM authentication tag length in bits. */
    private int tagLengthBits = 128;

    /** Rows re-wrapped per transaction by the key-rotation job. */
    private int rewrapChunkSize = 1_000;

    public boolean isConfigured() {
        return !activeKeyId.isBlank()
                && keys.containsKey(activeKeyId)
                && !tokenHashPepper.isBlank();
    }
}
