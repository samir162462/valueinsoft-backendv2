package com.example.valueinsoftbackend.Model.Request;

public record CurrentShiftRequest(
        int branchId,
        boolean getDetails
) {}
