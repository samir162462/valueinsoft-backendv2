package com.example.valueinsoftbackend;


import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosOrder;
import com.example.valueinsoftbackend.Model.OrderDetails;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;


@SpringBootApplication

public class ValueinsoftBackendApplication {

    public static String trustedHost = "http://localhost:3000";
public static String DatabaseOwner = "krdszavicoqkpf";
    public static boolean  goOnline =true;

    public static void main(String[] args) {
        SpringApplication.run(ValueinsoftBackendApplication.class, args);

        DbBranch.deleteBranch(1012);

    }



}
