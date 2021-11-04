package com.example.valueinsoftbackend.Model;

import java.util.ArrayList;
import java.util.Random;

public class OrderShift {
    int orderId ;
    String orderTime;
    String clientName;
    String orderType ;
    int orderDiscount;
    int orderTotal;
    String salesUser;
    ArrayList<OrderDetails> orderDetails;



    public OrderShift(int orderId, String orderTime, String clientName, String orderType, int orderDiscount, int orderTotal, String salesUser, ArrayList<OrderDetails> orderDetails) {
        this.orderId = hashCode(orderTime,clientName);
        this.orderTime = orderTime;
        this.clientName = clientName;
        this.orderType = orderType;
        this.orderDiscount = orderDiscount;
        this.orderTotal = orderTotal;
        this.salesUser = salesUser;
        this.orderDetails = orderDetails;
    }

    private int hashCode(String name , String buyDay) {
        Random rand = new Random();
        int hashnum = 0 ;
        hashnum = (int) rand.nextInt(1000) * name.hashCode() * buyDay.hashCode();
        if (hashnum<0)
        {
            hashnum = hashnum*-1;
        }
        if (hashnum==0)
        {
            hashnum = (int) rand.nextInt(1000) *(hashnum+1);
        }

        return hashnum;
    }


    public int getOrderId() {
        return orderId;
    }

    public String getOrderTime() {
        return orderTime;
    }

    public String getClientName() {
        return clientName;
    }

    public String getOrderType() {
        return orderType;
    }

    public int getOrderTotal() {
        return orderTotal;
    }

    public String getSalesUser() {
        return salesUser;
    }

    public int getOrderDiscount() {
        return orderDiscount;
    }

    public ArrayList<OrderDetails> getOrderDetails() {
        return orderDetails;
    }

    @Override
    public String toString() {
        return "OrderShift{" +
                "orderId=" + orderId +
                ", orderTime='" + orderTime + '\'' +
                ", clientName='" + clientName + '\'' +
                ", orderType='" + orderType + '\'' +
                ", orderDiscount=" + orderDiscount +
                ", orderTotal=" + orderTotal +
                ", salesUser='" + salesUser + '\'' +
                ", orderDetails=" + orderDetails +
                '}';
    }
}
