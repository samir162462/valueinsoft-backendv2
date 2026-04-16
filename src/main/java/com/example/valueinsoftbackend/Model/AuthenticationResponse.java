package com.example.valueinsoftbackend.Model;

import java.util.HashMap;
import java.util.Map;

/**
 * Modern Java Record replacement for AuthenticationRespone (fixing spelling).
 */
public record AuthenticationResponse(String jwt, String username, String role) {

    /**
     * Legacy compatibility method for returning data as a Map.
     */
    public Map<String, String> getData() {
        Map<String, String> map = new HashMap<>();
        map.put("username", username);
        map.put("jwt", jwt);
        map.put("role", role);
        return map;
    }
}
