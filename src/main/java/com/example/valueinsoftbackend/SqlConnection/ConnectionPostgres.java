package com.example.valueinsoftbackend.SqlConnection;

import com.example.valueinsoftbackend.ValueinsoftBackendApplication;

import java.sql.Connection;
import java.sql.DriverManager;

public class ConnectionPostgres {

    public static Connection getConnection() throws Exception {

        // netlify pricing 19$ month
        // https://www.pgsclusters.com/ 5$ month


        try {
            if (ValueinsoftBackendApplication.goOnline) {
                //Online Database Heroku
                ValueinsoftBackendApplication.DatabaseOwner = "postgresAdmin";
                Class.forName("org.postgresql.Driver");
                String url = "jdbc:postgresql://postgresql-123969-0.cloudclusters.net:18997/VLS";

                Connection conn = DriverManager.getConnection(url, "postgresAdmin", "Sa123456789");
                return conn;
            } else {
                //Local Database
                ValueinsoftBackendApplication.DatabaseOwner = "postgres";
                Class.forName("org.postgresql.Driver");
                String url = "jdbc:postgresql://localhost:5432/localvls";
                Connection conn = DriverManager.getConnection(url, "postgres", "0000");
                return conn;
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return null;
        }


    }


}
