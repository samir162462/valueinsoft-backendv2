package com.example.valueinsoftbackend.fx.service;

public class FxDeepSeekRateException extends RuntimeException {

    private final boolean transientFailure;
    private final String rawResponse;

    public FxDeepSeekRateException(String message, boolean transientFailure) {
        this(message, transientFailure, null, null);
    }

    public FxDeepSeekRateException(String message, boolean transientFailure, String rawResponse, Throwable cause) {
        super(message, cause);
        this.transientFailure = transientFailure;
        this.rawResponse = rawResponse;
    }

    public boolean isTransientFailure() {
        return transientFailure;
    }

    public String getRawResponse() {
        return rawResponse;
    }
}
