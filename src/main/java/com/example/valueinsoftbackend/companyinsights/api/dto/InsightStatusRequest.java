package com.example.valueinsoftbackend.companyinsights.api.dto;

/**
 * Body for PATCH /company-insights/{id}/status. status ∈ SEEN | DISMISSED | RESOLVED.
 */
public record InsightStatusRequest(String status, String note) {
}
