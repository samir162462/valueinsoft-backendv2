package com.example.valueinsoftbackend.SqlConnection;

import com.example.valueinsoftbackend.ValueinsoftBackendApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@Component
public class ConnectionPostgres {

    private static String url;
    private static String username;
    private static String password;

    public ConnectionPostgres(
            @Value("${spring.datasource.url}") String configuredUrl,
            @Value("${spring.datasource.username}") String configuredUsername,
            @Value("${spring.datasource.password:}") String configuredPassword,
            @Value("${vls.database.owner:${spring.datasource.username:postgres}}") String databaseOwner) {
        url = configuredUrl;
        username = configuredUsername;
        password = configuredPassword;
        ValueinsoftBackendApplication.DatabaseOwner = (databaseOwner == null || databaseOwner.isBlank())
                ? configuredUsername
                : databaseOwner;
    }

    public static Connection getConnection() throws SQLException {
        if (url == null || url.isBlank() || username == null || username.isBlank()) {
            throw new IllegalStateException("Database connection properties are not configured");
        }
        return DriverManager.getConnection(url, username, password);
    }
}
