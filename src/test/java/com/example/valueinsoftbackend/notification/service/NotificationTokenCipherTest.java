package com.example.valueinsoftbackend.notification.service;

import com.example.valueinsoftbackend.notification.config.NotificationCipherProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTokenCipherTest {
    @Test
    void aesGcmUsesRandomIvAndPepperedHashWithoutPersistingPlaintext() {
        NotificationCipherProperties properties = properties((byte) 7, (byte) 19);
        NotificationTokenCipher cipher = new NotificationTokenCipher(properties);

        var first = cipher.encrypt("native-push-token");
        var second = cipher.encrypt("native-push-token");

        assertThat(first.keyId()).isEqualTo("k1");
        assertThat(first.encrypted()).isNotEqualTo(second.encrypted());
        assertThat(first.hash()).containsExactly(second.hash());
        assertThat(new String(first.encrypted(), StandardCharsets.UTF_8))
                .doesNotContain("native-push-token");
        assertThat(cipher.decrypt(first.encrypted(), first.keyId()))
                .isEqualTo("native-push-token");
    }

    @Test
    void differentPepperChangesLookupHashAndWrongKeyCannotDecrypt() {
        NotificationTokenCipher first =
                new NotificationTokenCipher(properties((byte) 1, (byte) 2));
        NotificationTokenCipher second =
                new NotificationTokenCipher(properties((byte) 3, (byte) 4));
        var encrypted = first.encrypt("same-token");

        assertThat(first.hash("same-token")).isNotEqualTo(second.hash("same-token"));
        assertThatThrownBy(() -> second.decrypt(encrypted.encrypted(), "k1"))
                .isInstanceOf(IllegalStateException.class);
    }

    private static NotificationCipherProperties properties(byte keyByte, byte pepperByte) {
        NotificationCipherProperties properties = new NotificationCipherProperties();
        properties.setActiveKeyId("k1");
        properties.setKeys(Map.of("k1", base64(keyByte)));
        properties.setTokenHashPepper(base64(pepperByte));
        return properties;
    }

    private static String base64(byte value) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, value);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
