package com.example.valueinsoftbackend.notification.provider;

import com.example.valueinsoftbackend.notification.config.ApnsProperties;
import com.example.valueinsoftbackend.notification.config.FcmProperties;
import com.example.valueinsoftbackend.notification.model.NotificationDevice;
import com.example.valueinsoftbackend.notification.model.PushOutboxItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PushProviderContractTest {
    @Test
    void fcmUsesHttpV1AndCachesOAuthTokenAtConfiguredLifetimeRatio() throws Exception {
        AtomicInteger oauthRequests = new AtomicInteger();
        AtomicInteger sendRequests = new AtomicInteger();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> sentBody = new AtomicReference<>();
        HttpServer server = server();
        server.createContext("/token", exchange -> {
            oauthRequests.incrementAndGet();
            respond(exchange, 200, "{\"access_token\":\"oauth-1\",\"expires_in\":3600}");
        });
        server.createContext("/v1/projects/phase3/messages:send", exchange -> {
            sendRequests.incrementAndGet();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            sentBody.set(new String(
                    exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 503, "{\"error\":{\"status\":\"UNAVAILABLE\"}}");
        });
        server.start();
        try {
            String base = "http://localhost:" + server.getAddress().getPort();
            FcmProperties properties = new FcmProperties();
            properties.setEnabled(true);
            properties.setProjectId("phase3");
            properties.setSendEndpoint(base + "/v1/projects/{projectId}/messages:send");
            properties.setServiceAccountJsonBase64(serviceAccount(base + "/token"));
            ObjectMapper mapper = new ObjectMapper();
            FcmOAuthTokenProvider tokens = new FcmOAuthTokenProvider(properties, mapper);
            FcmV1PushProvider provider = new FcmV1PushProvider(properties, tokens, mapper);
            PushSendRequest request = new PushSendRequest(
                    "native-fcm-token", device("fcm"), outbox("fcm"));

            assertThat(provider.send(request).httpStatus()).isEqualTo(503);
            assertThat(provider.send(request).httpStatus()).isEqualTo(503);
            assertThat(oauthRequests).hasValue(1);
            assertThat(sendRequests).hasValue(2);
            assertThat(authorization.get()).isEqualTo("Bearer oauth-1");
            assertThat(sentBody.get())
                    .contains("\"message\"", "\"token\":\"native-fcm-token\"");

            provider.invalidateCredentials();
            provider.send(request);
            assertThat(oauthRequests).hasValue(2);
            provider.close();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void apnsUsesProviderJwtRequiredHeadersAndSyntheticBadTokenProbe() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> topic = new AtomicReference<>();
        HttpServer server = server();
        server.createContext("/3/device/", exchange -> {
            requests.incrementAndGet();
            authorization.set(exchange.getRequestHeaders().getFirst("authorization"));
            topic.set(exchange.getRequestHeaders().getFirst("apns-topic"));
            if (exchange.getRequestURI().getPath().endsWith("0".repeat(64))) {
                respond(exchange, 400, "{\"reason\":\"BadDeviceToken\"}");
            } else {
                exchange.getResponseHeaders().add("apns-id", "apns-message-1");
                respond(exchange, 200, "");
            }
        });
        server.start();
        try {
            String base = "http://localhost:" + server.getAddress().getPort();
            ApnsProperties properties = new ApnsProperties();
            properties.setEnabled(true);
            properties.setTeamId("TEAM123456");
            properties.setKeyId("KEY1234567");
            properties.setPrivateKeyBase64(apnsPrivateKey());
            properties.setProductionHost(base);
            properties.setSandboxHost(base);
            properties.setCredentialProbeTopic("com.valueinsoft.phase3");
            ApnsPushProvider provider = new ApnsPushProvider(properties);

            PushProviderResponse response = provider.send(new PushSendRequest(
                    "abcdef123456", device("apns"), outbox("apns")));
            assertThat(response.httpStatus()).isEqualTo(200);
            assertThat(authorization.get()).startsWith("bearer ");
            assertThat(topic.get()).isEqualTo("com.valueinsoft.phase3");
            assertThat(provider.probeCredentials().healthy()).isTrue();
            assertThat(requests).hasValue(2);
            provider.close();
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer server() throws IOException {
        return HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    }

    private static void respond(HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String serviceAccount(String tokenUri) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String pem = pem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
        String json = new ObjectMapper().writeValueAsString(Map.of(
                "client_email", "phase3@local.test",
                "private_key", pem,
                "token_uri", tokenUri));
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String apnsPrivateKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        String pem = pem("PRIVATE KEY", generator.generateKeyPair().getPrivate().getEncoded());
        return Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.UTF_8));
    }

    private static String pem(String type, byte[] bytes) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(bytes)
                + "\n-----END " + type + "-----\n";
    }

    private static NotificationDevice device(String provider) {
        return new NotificationDevice(
                5, UUID.randomUUID(), 7, 9, null, "install",
                provider, "com.valueinsoft.phase3",
                "apns".equals(provider) ? "production" : "none",
                "apns".equals(provider) ? "ios" : "android",
                1, new byte[]{1}, "k1", new byte[32],
                "en", "UTC", 1, "active", 0, null, OffsetDateTime.now());
    }

    private static PushOutboxItem outbox(String provider) {
        return new PushOutboxItem(
                OffsetDateTime.now(), 1, UUID.randomUUID(), new byte[32],
                9, 11, 12, UUID.randomUUID(), 7, 5, 1,
                provider, "normal",
                "fcm".equals(provider)
                        ? "{\"notification\":{\"title\":\"Test\"},\"data\":{}}"
                        : "{\"aps\":{\"alert\":{\"title\":\"Test\"}}}",
                1, 50, "collapse", 3_600, "claimed", 1, 6);
    }
}
