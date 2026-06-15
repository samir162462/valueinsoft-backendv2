package com.example.valueinsoftbackend.Model.Request.PlatformAdmin;

import jakarta.validation.constraints.Positive;

public class PlatformCompanyOwnerUpdateRequest {

    @Positive(message = "userId must be positive")
    private int userId;

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }
}
