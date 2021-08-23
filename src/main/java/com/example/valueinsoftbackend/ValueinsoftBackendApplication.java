package com.example.valueinsoftbackend;

import Model.Product;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.ArrayList;

@SpringBootApplication

public class ValueinsoftBackendApplication {
	public static ArrayList<Product>productArrayList = new ArrayList<>();




	public static void main(String[] args) {
		Product p1 = new Product(111,"Iphone 12 Pro Max","18/9/2021","30",15000,17000,20000,"Apple","Mobiles","SamirMohamed","151f5ds5155dser43","This is good product",88,"01258745895","29850336521474",1,"Used");
		Product p2 = new Product(112,"Iphone 11 Pro ","1/3/2021","0",10000,12000,13000,"Apple","Mobiles","","5151fds5155dser43","This is New product",100,"","",3,"New");
		Product p3 = new Product(113,"Iphone 8 Pro ","1/3/2021","0",10000,12000,13000,"Apple","Mobiles","","6151f64ds5155dser43","This is New product",100,"","",3,"New");
		Product p4 = new Product(114,"Iphone 4 Pro ","1/3/2021","0",10000,12000,13000,"Apple","Mobiles","","7151fds5155dser43","This is New product",100,"","",3,"New");
		Product p5 = new Product(115,"Iphone G Pro ","1/3/2021","0",10000,12000,13000,"Apple","Mobiles","","8151fds5155dser43","This is New product",100,"","",3,"New");
		Product p6 = new Product(116,"Iphone 5G Pro ","1/3/2021","0",10000,12000,13000,"Apple","Mobiles","","9151fds5155dser43","This is New product",100,"","",3,"New");
		Product p7 = new Product(117,"Iphone 6s Pro ","1/3/2021","0",10000,12000,13000,"Apple","Mobiles","","1051fds5155dser43","This is New product",100,"","",3,"New");
		Product p8 = new Product(118,"Iphone 3G Pro ","1/3/2021","0",10000,12000,13000,"Apple","Mobiles","","1251fds5155dser43","This is New product",100,"","",3,"New");
		Product p9 = new Product(119,"Iphone X Pro ","1/3/2021","0",10000,12000,13000,"Apple","Mobiles","","1151fds5155dser43","This is New product",100,"","",3,"New");
		Product p10 = new Product(120,"Iphone 7 Pro ","1/3/2021","0",10000,12000,13000,"Apple","Mobiles","","1351fds5155dser43","This is New product",100,"","",3,"New");
		Product p11 = new Product(121,"Iphone 8 Pro ","1/3/2021","0",10000,12000,13000,"Apple","Mobiles","","14541fds5155dser43","This is New product",100,"","",3,"New");
		Product p12 = new Product(122,"Iphone 11 Pro ","1/3/2021","0",10000,12000,13000,"Apple","Mobiles","","1651fds5155dser43","This is New product",100,"","",3,"New");
		Product p13 = new Product(123,"Iphone 11 Pro Max","9/9/2021","30",12000,13000,14000,"Apple","Mobiles","SamirMohamed","17548fds141fsd55f","This is good used product",98,"0125487956","298548754515865",1,"Used");

		productArrayList.add(p1);
		productArrayList.add(p2);
		productArrayList.add(p3);
		productArrayList.add(p4);
		productArrayList.add(p5);
		productArrayList.add(p6);
		productArrayList.add(p7);
		productArrayList.add(p8);
		productArrayList.add(p9);
		productArrayList.add(p10);
		productArrayList.add(p11);
		productArrayList.add(p12);
		productArrayList.add(p13);
		SpringApplication.run(ValueinsoftBackendApplication.class, args);
	}

}
