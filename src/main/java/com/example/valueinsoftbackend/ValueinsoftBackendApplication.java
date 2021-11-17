package com.example.valueinsoftbackend;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication

public class ValueinsoftBackendApplication {


public static String DatabaseOwner = "krdszavicoqkpf";
    public static boolean  goOnline =true;

    public static void main(String[] args) {
        SpringApplication.run(ValueinsoftBackendApplication.class, args);
    }

}
