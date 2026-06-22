package com.example.valueinsoftbackend.Model.Response;

import java.sql.Timestamp;

public record CreateOrderResult(
        int orderId,
        String receiptNumber,
        boolean idempotencyHit,
        Integer shiftId,
        Timestamp orderTime
) {}
