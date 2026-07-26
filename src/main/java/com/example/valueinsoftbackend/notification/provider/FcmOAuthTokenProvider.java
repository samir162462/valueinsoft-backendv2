package com.example.valueinsoftbackend.notification.provider;

import com.example.valueinsoftbackend.notification.config.FcmProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Component
public class FcmOAuthTokenProvider {
    private final FcmProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient client;
    private volatile CachedToken cached;

    public FcmOAuthTokenProvider(FcmProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .build();
    }

    public synchronized String accessToken() {
        CachedToken current = cached;
        if (current != null && Instant.now().isBefore(current.refreshAt())) {
            return current.value();
        }
        if (!properties.isConfigured()) {
            throw new IllegalStateException("FCM credentials are not configured");
        }
        try {
            JsonNode account = objectMapper.readTree(Base64.getDecoder().decode(
                    properties.getServiceAccountJsonBase64()));
            String email = required(account, "client_email");
            String key = required(account, "private_key");
            String tokenUri = account.path("token_uri").asText(properties.getTokenEndpoint());
            Instant now = Instant.now();
            String assertion = jwt(email, key, tokenUri, now);
            String body = "grant_type="
                    + URLEncoder.encode(
                    "urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8)
                    + "&assertion=" + URLEncoder.encode(assertion, StandardCharsets.UTF_8);
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create(tokenUri))
                            .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "FCM OAuth token request failed with HTTP " + response.statusCode());
            }
            JsonNode token = objectMapper.readTree(response.body());
            String value = required(token, "access_token");
            long lifetime = Math.max(60, token.path("expires_in").asLong(3600));
            long refreshSeconds = Math.max(30,
                    (long) (lifetime * properties.getTokenRefreshRatio()));
            cached = new CachedToken(value, now.plusSeconds(refreshSeconds));
            return value;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("FCM OAuth token request interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("FCM OAuth token acquisition failed", exception);
        }
    }

    public synchronized void invalidate() {
        cached = null;
    }

    private String jwt(String email, String pem, String audience, Instant now)
            throws Exception {
        String header = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        String claims = base64Url(objectMapper.writeValueAsString(java.util.Map.of(
                "iss", email,
                "scope", properties.getScope(),
                "aud", audience,
                "iat", now.getEpochSecond(),
                "exp", now.plusSeconds(3600).getEpochSecond())));
        String signingInput = header + "." + claims;
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey(pem));
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(signature.sign());
    }

    private static PrivateKey privateKey(String pem) throws Exception {
        String normalized = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        return KeyFactory.getInstance("RSA").generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(normalized)));
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String required(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value.isBlank()) {
            throw new IllegalStateException("FCM service account is missing " + field);
        }
        return value;
    }

    private record CachedToken(String value, Instant refreshAt) {
    }
}
