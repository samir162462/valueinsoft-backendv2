package com.example.valueinsoftbackend;

public class User {

    String UserId;
    String UserName;
    String UserPassword;

    public User(String userId, String userName, String userPassword) {
        UserId = userId;
        UserName = userName;
        UserPassword = userPassword;
    }

    public String getUserId() {
        return UserId;
    }

    public String getUserName() {
        return UserName;
    }

    public String getUserPassword() {
        return UserPassword;
    }
}
