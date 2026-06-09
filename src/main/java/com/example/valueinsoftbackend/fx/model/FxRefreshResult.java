package com.example.valueinsoftbackend.fx.model;

import java.time.LocalDate;

public record FxRefreshResult(
        String status,
        String message,
        Long snapshotId,
        LocalDate weekStartDate,
        boolean deepSeekCalled,
        FxCompanyProcessingSummary companyProcessingSummary
) {
    public static FxRefreshResult skipped(String message, LocalDate weekStartDate, boolean deepSeekCalled) {
        return new FxRefreshResult("SKIPPED", message, null, weekStartDate, deepSeekCalled, FxCompanyProcessingSummary.empty());
    }

    public static FxRefreshResult failed(String message, Long snapshotId, LocalDate weekStartDate, boolean deepSeekCalled) {
        return new FxRefreshResult("FAILED", message, snapshotId, weekStartDate, deepSeekCalled, FxCompanyProcessingSummary.empty());
    }
}
