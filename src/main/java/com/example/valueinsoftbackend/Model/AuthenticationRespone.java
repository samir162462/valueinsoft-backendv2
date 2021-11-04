package com.example.valueinsoftbackend.Model;

import java.util.HashMap;
import java.util.Map;

public class AuthenticationRespone {

    private final String jwt;
    private final String username;

    public AuthenticationRespone(String jwt, String username) {
        this.jwt = jwt;
        this.username = username;
    }

    public String getJwt() {
        return jwt;
    }
    public Map<String, String> getData() {
        HashMap<String, String> map = new HashMap<>();
        map.put("username", username);
        map.put("jwt", jwt);
        return map;
    }

}
