package com.example.valueinsoftbackend.notification.provider;

import com.example.valueinsoftbackend.notification.config.ApnsProperties;
import io.jsonwebtoken.Jwts;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class ApnsPushProvider implements PushProvider {
    private final ApnsProperties properties;
    private final HttpClient client;
    private final Semaphore streams;
    private final ReentrantLock jwtLock = new ReentrantLock();
    private volatile CachedJwt cachedJwt;
    private volatile Instant lastForcedRefresh = Instant.EPOCH;

    public ApnsPushProvider(ApnsProperties properties) {
        this.properties = properties;
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .build();
        this.streams = new Semaphore(properties.getMaxConcurrentStreams());
    }

    @Autowired
    public ApnsPushProvider(ApnsProperties properties, MeterRegistry meters) {
        this(properties);
        Gauge.builder("notification.apns.inflight", streams,
                        semaphore -> properties.getMaxConcurrentStreams()
                                - semaphore.availablePermits())
                .register(meters);
        Gauge.builder("notification.apns.jwt.age_seconds", this,
                        provider -> provider.jwtAgeSeconds())
                .register(meters);
    }

    @Override
    public String provider() {
        return "apns";
    }

    @Override
    public PushProviderResponse send(PushSendRequest request) {
        if (!properties.isEnabled() || !properties.isConfigured()) {
            return PushProviderResponse.transport(
                    new IllegalStateException("APNs provider is disabled or unconfigured"),
                    Duration.ZERO);
        }
        Instant started = Instant.now();
        boolean acquired = false;
        try {
            acquired = streams.tryAcquire(
                    properties.getRequestTimeoutSeconds(),
                    java.util.concurrent.TimeUnit.SECONDS);
            if (!acquired) {
                return PushProviderResponse.transport(
                        new IllegalStateException("APNs stream limit timed out"),
                        Duration.between(started, Instant.now()));
            }
            String host = properties.hostFor(request.device().apnsEnvironment());
            String tokenPath = URLEncoder.encode(
                    request.credential(), StandardCharsets.UTF_8);
            HttpRequest.Builder httpRequest =
                    HttpRequest.newBuilder(URI.create(host + "/3/device/" + tokenPath))
                            .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                            .header("authorization", "bearer " + jwt())
                            .header("apns-topic", request.device().appBundleId())
                            .header("apns-push-type", "alert")
                            .header("apns-priority",
                                    "critical".equals(request.outbox().priority()) ? "10" : "5")
                            .header("apns-expiration", Long.toString(
                                    Instant.now().plusSeconds(
                                            request.outbox().ttlSeconds()).getEpochSecond()))
                            .POST(HttpRequest.BodyPublishers.ofString(
                                    request.outbox().payloadJson()));
            if (request.outbox().collapseKey() != null
                    && !request.outbox().collapseKey().isBlank()) {
                httpRequest.header("apns-collapse-id", request.outbox().collapseKey());
            }
            HttpResponse<String> response = client.send(
                    httpRequest.build(),
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
        } finally {
            if (acquired) {
                streams.release();
            }
        }
    }

    @Override
    public CredentialProbeResult probeCredentials() {
        if (!properties.isEnabled() || !properties.isCredentialProbeEnabled()) {
            return new CredentialProbeResult(true, "disabled");
        }
        if (!properties.isConfigured()) {
            return new CredentialProbeResult(false, "APNs credentials are not configured");
        }
        if (properties.getCredentialProbeTopic() == null
                || properties.getCredentialProbeTopic().isBlank()) {
            return new CredentialProbeResult(
                    false, "APNs credential probe topic is not configured");
        }
        try {
            String syntheticToken = "0".repeat(64);
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create(
                                    properties.getProductionHost()
                                            + "/3/device/" + syntheticToken))
                            .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                            .header("authorization", "bearer " + jwt())
                            .header("apns-topic", properties.getCredentialProbeTopic())
                            .header("apns-push-type", "background")
                            .header("apns-priority", "5")
                            .POST(HttpRequest.BodyPublishers.ofString(
                                    "{\"aps\":{\"content-available\":1}}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            boolean expected = response.statusCode() == 400
                    && response.body() != null
                    && response.body().contains("\"BadDeviceToken\"");
            return new CredentialProbeResult(expected,
                    expected ? "authenticated; synthetic token rejected as expected"
                            : "unexpected APNs probe response HTTP " + response.statusCode());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new CredentialProbeResult(false, "APNs credential probe interrupted");
        } catch (Exception exception) {
            return new CredentialProbeResult(false, exception.getMessage());
        }
    }

    @Override
    public void invalidateCredentials() {
        jwtLock.lock();
        try {
            Instant now = Instant.now();
            CachedJwt current = cachedJwt;
            Instant floorFrom = current == null ? lastForcedRefresh : current.issuedAt();
            if (!now.isBefore(floorFrom.plus(Duration.ofMinutes(
                    properties.getJwtForcedRefreshFloorMinutes())))) {
                cachedJwt = null;
                lastForcedRefresh = now;
            }
        } finally {
            jwtLock.unlock();
        }
    }

    @Override
    public void close() {
        client.close();
    }

    private String jwt() {
        CachedJwt current = cachedJwt;
        if (current != null && Instant.now().isBefore(current.expiresAt())) {
            return current.value();
        }
        jwtLock.lock();
        try {
            current = cachedJwt;
            if (current != null && Instant.now().isBefore(current.expiresAt())) {
                return current.value();
            }
            try {
                Instant now = Instant.now();
                String value = Jwts.builder()
                        .header().keyId(properties.getKeyId()).and()
                        .issuer(properties.getTeamId())
                        .issuedAt(Date.from(now))
                        .signWith(privateKey(), Jwts.SIG.ES256)
                        .compact();
                cachedJwt = new CachedJwt(
                        value, now, now.plus(Duration.ofMinutes(
                                properties.getJwtRefreshMinutes())));
                return value;
            } catch (Exception exception) {
                throw new IllegalStateException("APNs provider-token generation failed", exception);
            }
        } finally {
            jwtLock.unlock();
        }
    }

    private PrivateKey privateKey() throws Exception {
        String pem = new String(
                Base64.getDecoder().decode(properties.getPrivateKeyBase64()),
                StandardCharsets.UTF_8);
        String normalized = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        return KeyFactory.getInstance("EC").generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(normalized)));
    }

    private double jwtAgeSeconds() {
        CachedJwt current = cachedJwt;
        return current == null ? 0
                : Math.max(0, Duration.between(
                        current.issuedAt(), Instant.now()).toSeconds());
    }

    private record CachedJwt(String value, Instant issuedAt, Instant expiresAt) {
    }
}
