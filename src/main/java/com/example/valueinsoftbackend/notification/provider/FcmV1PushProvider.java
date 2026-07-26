package com.example.valueinsoftbackend.notification.provider;

import com.example.valueinsoftbackend.notification.config.FcmProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class FcmV1PushProvider implements PushProvider {
    private final FcmProperties properties;
    private final FcmOAuthTokenProvider tokens;
    private final ObjectMapper objectMapper;
    private final HttpClient client;

    public FcmV1PushProvider(FcmProperties properties,
                             FcmOAuthTokenProvider tokens,
                             ObjectMapper objectMapper) {
        this.properties = properties;
        this.tokens = tokens;
        this.objectMapper = objectMapper;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .build();
    }

    @Override
    public String provider() {
        return "fcm";
    }

    @Override
    public PushProviderResponse send(PushSendRequest request) {
        if (!properties.isEnabled() || !properties.isConfigured()) {
            return PushProviderResponse.transport(
                    new IllegalStateException("FCM provider is disabled or unconfigured"),
                    Duration.ZERO);
        }
        return sendOnce(request);
    }

    private PushProviderResponse sendOnce(PushSendRequest request) {
        Instant started = Instant.now();
        try {
            Map<String, Object> message = objectMapper.readValue(
                    request.outbox().payloadJson(), new TypeReference<>() {});
            message = new LinkedHashMap<>(message);
            message.put("token", request.credential());
            String body = objectMapper.writeValueAsString(Map.of("message", message));
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create(properties.resolvedSendEndpoint()))
                            .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                            .header("Authorization", "Bearer " + tokens.accessToken())
                            .header("Content-Type", "application/json; charset=UTF-8")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            return new PushProviderResponse(
                    response.statusCode(), response.body(), response.headers().map(),
                    Duration.between(started, Instant.now()), null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return PushProviderResponse.transport(
                    exception, Duration.between(started, Instant.now()));
        } catch (Exception exception) {
            return PushProviderResponse.transport(
                    exception, Duration.between(started, Instant.now()));
        }
    }

    @Override
    public CredentialProbeResult probeCredentials() {
        if (!properties.isEnabled() || !properties.isCredentialProbeEnabled()) {
            return new CredentialProbeResult(true, "disabled");
        }
        try {
            tokens.accessToken();
            if (properties.getCredentialExpiresAt() != null
                    && properties.getCredentialExpiresAt().isBefore(
                    java.time.OffsetDateTime.now().plusDays(
                            properties.getCredentialExpiryWarningDays()))) {
                return new CredentialProbeResult(
                        false, "FCM credential expires within warning window");
            }
            return new CredentialProbeResult(true, "oauth token acquired");
        } catch (RuntimeException exception) {
            return new CredentialProbeResult(false, exception.getMessage());
        }
    }

    @Override
    public void invalidateCredentials() {
        tokens.invalidate();
    }

    @Override
    public void close() {
        client.close();
    }
}
