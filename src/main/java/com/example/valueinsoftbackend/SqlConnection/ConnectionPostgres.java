package com.example.valueinsoftbackend.SqlConnection;

import com.example.valueinsoftbackend.ValueinsoftBackendApplication;

import java.sql.Connection;
import java.sql.DriverManager;

public class ConnectionPostgres {

    public static Connection getConnection() throws Exception {


        if (!ValueinsoftBackendApplication.goOnline) {
            //Local Database
            ValueinsoftBackendApplication.DatabaseOwner = "postgres";
            Class.forName("org.postgresql.Driver");
            String url = "jdbc:postgresql://localhost:5432/postgres";
            Connection conn = DriverManager.getConnection(url, "postgres", "0000");
            return conn;
        } else {
            //Online Database Heroku
            ValueinsoftBackendApplication.DatabaseOwner = "krdszavicoqkpf";
            Class.forName("org.postgresql.Driver");
            String url = "jdbc:postgresql://ec2-176-34-105-15.eu-west-1.compute.amazonaws.com:5432/ddq97lo8kkor9k";

            Connection conn = DriverManager.getConnection(url, "krdszavicoqkpf", "48892245c9bbf412155094a8b8e3c64ab86f80280c01fb9b558c6a50531ee62f");
            return conn;
        }


    }


}
