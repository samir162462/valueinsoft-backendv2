package com.example.valueinsoftbackend;

import Model.Product;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.ArrayList;

@SpringBootApplication

public class ValueinsoftBackendApplication {
	public static ArrayList<Product>productArrayList = new ArrayList<>();




	public static void main(String[] args) {
		Product p1 = new Product(10,"Iphone 12 Pro Max","18/9/2021","30",15000,17000,20000,"Apple","Mobiles","SamirMohamed","151fds5155dser43","This is good product",88,"01258745895","29850336521474",1,"Used");
		Product p2 = new Product(111,"Iphone 10 Pro ","1/3/2021","0",10000,12000,13000,"Apple","Mobiles","","151fds5155dser43","This is New product",100,"","",3,"New");
		productArrayList.add(p1);
		productArrayList.add(p2);
		SpringApplication.run(ValueinsoftBackendApplication.class, args);
	}

}
