package com.example.valueinsoftbackend.SqlConnection;

import com.example.valueinsoftbackend.ValueinsoftBackendApplication;

import java.sql.Connection;
import java.sql.DriverManager;

public class ConnectionPostgres {

    public static Connection getConnection() throws Exception {

        //https://api.elephantsql.com/console/aac0e90f-f846-4358-aac4-2f877184e385/details#   ---- > Elphent Site

        try {
            if (!ValueinsoftBackendApplication.goOnline) {
                //Local Database
                ValueinsoftBackendApplication.DatabaseOwner = "postgres";
                Class.forName("org.postgresql.Driver");
                String url = "jdbc:postgresql://localhost:5432/localvls";
                Connection conn = DriverManager.getConnection(url, "postgres", "0000");
                return conn;
            } else {
                //Online Database Heroku
                ValueinsoftBackendApplication.DatabaseOwner = "qnnzxbni";
                Class.forName("org.postgresql.Driver");
                String url = "jdbc:postgresql://kandula.db.elephantsql.com:5432/qnnzxbni";

                Connection conn = DriverManager.getConnection(url, "qnnzxbni", "y7H5olBbx2Vbs0BDWC3jCKC23ffjWhHR");
                return conn;
            }
        }catch (Exception e)
        {
            System.out.println(e.getMessage());
            return null;
        }




    }


}
