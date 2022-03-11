package com.example.valueinsoftbackend.SqlConnection;

import com.example.valueinsoftbackend.ValueinsoftBackendApplication;

import java.sql.Connection;
import java.sql.DriverManager;

public class ConnectionPostgres {

    public static Connection getConnection() throws Exception {

        try {
            if (!ValueinsoftBackendApplication.goOnline) {
                //Local Database
                ValueinsoftBackendApplication.DatabaseOwner = "postgres";
                Class.forName("org.postgresql.Driver");
                String url = "jdbc:postgresql://localhost:5432/postgres";
                Connection conn = DriverManager.getConnection(url, "postgres", "0000");
                return conn;
            } else {
                //Online Database Heroku
                ValueinsoftBackendApplication.DatabaseOwner = "xdbclqyeafclrb";
                Class.forName("org.postgresql.Driver");
                String url = "jdbc:postgresql://ec2-52-215-22-82.eu-west-1.compute.amazonaws.com:5432/dcp1madep62ah3";

                Connection conn = DriverManager.getConnection(url, "xdbclqyeafclrb", "0961698bab96b15477229c7686b5598da85b35387127bc9c98e3012e5429260f");
                return conn;
            }
        }catch (Exception e)
        {
            System.out.println(e.getMessage());
            return null;
        }




    }


}
