package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.pos.offline.model.PosIdempotencyModel;

public record IdempotencyClaimResult(
        PosIdempotencyModel record,
        boolean newlyClaimed,
        boolean payloadMatches
) {
}
