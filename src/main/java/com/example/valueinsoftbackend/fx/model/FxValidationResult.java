package com.example.valueinsoftbackend.fx.model;

public record FxValidationResult(
        boolean valid,
        String message
) {
    public static FxValidationResult accepted() {
        return new FxValidationResult(true, "VALID");
    }

    public static FxValidationResult rejected(String message) {
        return new FxValidationResult(false, message);
    }
}
