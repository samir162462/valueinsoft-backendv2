package com.example.valueinsoftbackend;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.Model.Branch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.ArrayList;

@SpringBootApplication
@EnableScheduling
@Slf4j
public class ValueinsoftBackendApplication {

    public static ArrayList<Branch> branchArrayList = new ArrayList<>();

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(ValueinsoftBackendApplication.class, args);
        DbBranch dbBranch = context.getBean(DbBranch.class);
        branchArrayList = (ArrayList<Branch>) dbBranch.getAllBranches();
        log.info("Loaded {} branches during startup", branchArrayList.size());
    }

}
