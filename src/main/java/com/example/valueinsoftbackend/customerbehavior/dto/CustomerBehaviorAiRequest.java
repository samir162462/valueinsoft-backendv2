package com.example.valueinsoftbackend.customerbehavior.dto;

public record CustomerBehaviorAiRequest(
        CustomerBehaviorFilter filter,
        boolean forceRefresh,
        String locale
) {
}
