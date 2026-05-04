package com.example.valueinsoftbackend.pos.offline.dto.response;

import java.util.List;

public record OfflineAdminReadiness(
        boolean canRecoverStuck,
        boolean canProcess,
        boolean canValidate,
        boolean canPost,
        boolean canRecalculateSummary,
        List<String> recoverBlockedReasons,
        List<String> processBlockedReasons,
        List<String> validateBlockedReasons,
        List<String> postBlockedReasons,
        boolean requiresReasonForPost,
        boolean requiresReasonForRecoverStuck,
        boolean requiresForceForPost
) {
}
