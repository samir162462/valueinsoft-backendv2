package com.example.valueinsoftbackend;

public class User {

    int UserId;
    String UserName;
    String UserPassword;


    public User(int userId, String userName, String userPassword) {
        UserId = userId;
        UserName = userName;
        UserPassword = userPassword;
    }

    public int getUserId() {
        return UserId;
    }

    public String getUserName() {
        return UserName;
    }

    public String getUserPassword() {
        return UserPassword;
    }
}
