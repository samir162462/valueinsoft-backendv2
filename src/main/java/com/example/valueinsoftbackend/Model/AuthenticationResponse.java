package com.example.valueinsoftbackend.Model;

import java.util.HashMap;
import java.util.Map;

/**
 * Modern Java Record replacement for AuthenticationRespone (fixing spelling).
 */
public record AuthenticationResponse(String jwt, String username, String role, boolean passwordResetRequired) {

    /**
     * Legacy compatibility method for returning data as a Map.
     */
    public Map<String, Object> getData() {
        Map<String, Object> map = new HashMap<>();
        map.put("username", username);
        map.put("jwt", jwt);
        map.put("role", role);
        map.put("passwordResetRequired", passwordResetRequired);
        return map;
    }
}
