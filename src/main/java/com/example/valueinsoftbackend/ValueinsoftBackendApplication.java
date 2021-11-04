package com.example.valueinsoftbackend;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosCategory;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosProduct;
import com.example.valueinsoftbackend.DatabaseRequests.DbUsers;
import com.example.valueinsoftbackend.Model.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;


import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

@SpringBootApplication

public class ValueinsoftBackendApplication {

    public static int currentUserId = 10;


    public static ArrayList<Product> productArrayList = new ArrayList<>();
    public static ArrayList<OrderShift> orderShiftArrayList = new ArrayList<>();
    public static ArrayList<ShiftPeriod> shiftPeriodArrayList = new ArrayList<>();


    public static void main(String[] args) {
        SpringApplication.run(ValueinsoftBackendApplication.class, args);
        DbPosCategory.getCategoryIdByBranchIdAndCateName(1022, "mate10");
    }

}
