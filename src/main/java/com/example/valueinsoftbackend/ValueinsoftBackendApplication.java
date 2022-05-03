package com.example.valueinsoftbackend;


import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosOrder;
import com.example.valueinsoftbackend.DatabaseRequests.DbSQL.DbSqlCloseIdles;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.OrderDetails;
import com.example.valueinsoftbackend.OnlinePayment.OPModel.PayMobAuth;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Type;
import java.util.*;


@SpringBootApplication

public class ValueinsoftBackendApplication {

    public static String trustedHost = "http://localhost:3000";
    public static String DatabaseOwner = "krdszavicoqkpf";
    public static String PAYMTOKEN = "ZXlKMGVYQWlPaUpLVjFRaUxDSmhiR2NpT2lKSVV6VXhNaUo5LmV5SmpiR0Z6Y3lJNklrMWxjbU5vWVc1MElpd2libUZ0WlNJNkltbHVhWFJwWVd3aUxDSndjbTltYVd4bFgzQnJJam94TmpnME5qZDkuTFVUTktWYVlKbkVkUzM1SDBOVDJyb21iYU1oaFBJZXhxbUg3LWcwOXNVcFJSTGM2ak05Vkh2SFV3Y2V5Q3JxNldpZ3JURGR2VGhlZVpUeHl3UUptZnc=";
    public static boolean goOnline = true;


    //init
    public static ArrayList<Branch> branchArrayList =new ArrayList<>();


    public static void main(String[] args) {
        SpringApplication.run(ValueinsoftBackendApplication.class, args);

        //DbBranch.AddBranch("Filfilco1","Sharkia,Egypt",1000);
        // for (int i = 1014; i <= 1019; i++) {
        //     DbBranch.deleteBranch(i,"public");

        // }
        //DbCompany.CreateCompanySchema(1086);
        // DbSqlCloseIdles.terminate();
        System.out.println( "${terminate.delay}");
        try {
            branchArrayList = DbBranch.getAllBranches();
            System.out.println(branchArrayList.size());
        }catch (Exception e)
        {

        }
    }

    @Scheduled(initialDelay = 1 * 60 * 1000, fixedDelayString = "${terminate.delay}")
    void terminateDatabaseIdleProcess() {
        System.out.println("Now is :" + new Date());
        DbSqlCloseIdles.terminate();

    }
    @Scheduled(cron="0 5 * * * *", zone="Europe/Istanbul") //everyDay At 6am
    public void doScheduledWork() {
        //complete scheduled work
        System.out.println("at every 1m");
    }

}
