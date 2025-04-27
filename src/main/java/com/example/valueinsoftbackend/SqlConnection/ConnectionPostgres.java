package com.example.valueinsoftbackend.SqlConnection;

import com.example.valueinsoftbackend.ValueinsoftBackendApplication;

import java.sql.Connection;
import java.sql.DriverManager;

public class ConnectionPostgres {

    public static Connection getConnection() throws Exception {



        try {
            if (ValueinsoftBackendApplication.goOnline) {
                //Online Database Heroku
                ValueinsoftBackendApplication.DatabaseOwner = "postgres";
                Class.forName("org.postgresql.Driver");
                String url = "jdbc:postgresql://ec2-3-68-75-162.eu-central-1.compute.amazonaws.com:5432/postgres";

                Connection conn = DriverManager.getConnection(url, "postgres", "0000");
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
