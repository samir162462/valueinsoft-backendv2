package com.example.valueinsoftbackend.Model.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public class AdminResetPasswordRequest {

    @NotBlank(message = "userName is required")
    private String userName;

    @NotBlank(message = "password is required")
    private String password;

    @Positive(message = "branchId must be positive")
    private int branchId;

    private boolean passwordResetRequired = true;

    public AdminResetPasswordRequest() {
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getBranchId() {
        return branchId;
    }

    public void setBranchId(int branchId) {
        this.branchId = branchId;
    }

    public boolean isPasswordResetRequired() {
        return passwordResetRequired;
    }

    public void setPasswordResetRequired(boolean passwordResetRequired) {
        this.passwordResetRequired = passwordResetRequired;
    }
}
