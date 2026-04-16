package com.example.valueinsoftbackend.Model;

import jakarta.validation.constraints.NotBlank;

public record AuthenticationRequest(
    @NotBlank(message = "username is required")
    String username,
    @NotBlank(message = "password is required")
    String password,
    String role
) {
    public AuthenticationRequest(String username, String password) {
        this(username, password, null);
    }
}
