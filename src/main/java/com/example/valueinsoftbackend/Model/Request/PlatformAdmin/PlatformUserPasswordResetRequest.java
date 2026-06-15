package com.example.valueinsoftbackend.Model.Request.PlatformAdmin;

import jakarta.validation.constraints.NotBlank;

public class PlatformUserPasswordResetRequest {

    @NotBlank(message = "userName is required")
    private String userName;

    @NotBlank(message = "password is required")
    private String password;

    private boolean passwordResetRequired = true;

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

    public boolean isPasswordResetRequired() {
        return passwordResetRequired;
    }

    public void setPasswordResetRequired(boolean passwordResetRequired) {
        this.passwordResetRequired = passwordResetRequired;
    }
}
