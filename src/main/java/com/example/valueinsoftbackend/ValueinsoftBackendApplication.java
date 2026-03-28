package com.example.valueinsoftbackend;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.DatabaseRequests.DbSQL.DbSqlCloseIdles;
import com.example.valueinsoftbackend.Model.Branch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayList;
import java.util.Date;

@SpringBootApplication
@Slf4j
public class ValueinsoftBackendApplication {

    public static String DatabaseOwner = "";
    public static ArrayList<Branch> branchArrayList = new ArrayList<>();

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(ValueinsoftBackendApplication.class, args);
        DbBranch dbBranch = context.getBean(DbBranch.class);
        branchArrayList = (ArrayList<Branch>) dbBranch.getAllBranches();
        log.info("Loaded {} branches during startup", branchArrayList.size());
    }

    @Scheduled(initialDelay = 60_000, fixedDelayString = "${terminate.delay}")
    void terminateDatabaseIdleProcess() {
        log.debug("Running idle connection cleanup at {}", new Date());
        DbSqlCloseIdles.terminate();
    }

    @Scheduled(cron = "0 5 * * * *", zone = "Europe/Istanbul")
    public void doScheduledWork() {
        log.debug("Scheduled heartbeat at {}", new Date());
    }
}
