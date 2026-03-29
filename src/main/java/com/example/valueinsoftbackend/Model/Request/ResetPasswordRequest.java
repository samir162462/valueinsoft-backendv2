package com.example.valueinsoftbackend.Model.Request;

import javax.validation.constraints.NotBlank;

public class ResetPasswordRequest {

    @NotBlank(message = "oldPassword is required")
    private String oldPassword;

    @NotBlank(message = "password is required")
    private String password;

    public ResetPasswordRequest() {
    }

    public String getOldPassword() {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
