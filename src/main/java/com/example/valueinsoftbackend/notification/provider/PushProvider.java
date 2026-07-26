package com.example.valueinsoftbackend.notification.provider;

public interface PushProvider {
    String provider();

    PushProviderResponse send(PushSendRequest request);

    default CredentialProbeResult probeCredentials() {
        return new CredentialProbeResult(true, "disabled");
    }

    default void invalidateCredentials() {
    }

    default void close() {
    }

    record CredentialProbeResult(boolean healthy, String detail) {
    }
}
