/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.OnlinePayment.OPModel;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class OrderRegistration {
    String auth_token;
    String delivery_needed;
    String amount_cents;
    String currency;
    int terminal_id;
    int merchant_order_id;
    ArrayList<Object> items;


    public OrderRegistration(String auth_token, String delivery_needed, String amount_cents, String currency, int terminal_id, int merchant_order_id, ArrayList items) {
        this.auth_token = auth_token;
        this.delivery_needed = delivery_needed;
        this.amount_cents = amount_cents;
        this.currency = currency;
        this.terminal_id = terminal_id;
        this.merchant_order_id = merchant_order_id;
        this.items = items;
    }

    public  int  createOrderRegistrationId(OrderRegistration orderRegistration) {
        String url = "https://accept.paymob.com/api/ecommerce/orders";
        RestTemplate restTemplate = new RestTemplate();
        // create headers
        HttpHeaders headers = new HttpHeaders();
        // set `content-type` header
        headers.setContentType(MediaType.APPLICATION_JSON);
        // set `accept` header
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        // create a map for post parameters
        Map<String, Object> map = new HashMap<>();
        map.put("auth_token",orderRegistration.auth_token);
        ArrayList<Object> items = new ArrayList<>();


        // build the request
        // send POST request
        ResponseEntity<String> response = restTemplate.postForEntity(url, orderRegistration, String.class);
        // check response status code
        System.out.println(response.getStatusCode());
        if (response.getStatusCode() == HttpStatus.CREATED) {
            System.out.println(response.getBody());
            return  Integer.valueOf(response.getBody().split(":")[1].split(",")[0].replaceAll("\"", "")) ;
        } else {
            return 0;
        }
    }

    public String getAuth_token() {
        return auth_token;
    }

    public void setAuth_token(String auth_token) {
        this.auth_token = auth_token;
    }

    public String getDelivery_needed() {
        return delivery_needed;
    }

    public void setDelivery_needed(String delivery_needed) {
        this.delivery_needed = delivery_needed;
    }

    public String getAmount_cents() {
        return amount_cents;
    }

    public void setAmount_cents(String amount_cents) {
        this.amount_cents = amount_cents;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public int getTerminal_id() {
        return terminal_id;
    }

    public void setTerminal_id(int terminal_id) {
        this.terminal_id = terminal_id;
    }

    public int getMerchant_order_id() {
        return merchant_order_id;
    }

    public void setMerchant_order_id(int merchant_order_id) {
        this.merchant_order_id = merchant_order_id;
    }

    public ArrayList getItems() {
        return items;
    }

    public void setItems(ArrayList items) {
        this.items = items;
    }

    @Override
    public String toString() {
        return "OrderRegistration{" +
                "auth_token='" + auth_token + '\'' +
                ", delivery_needed='" + delivery_needed + '\'' +
                ", amount_cents='" + amount_cents + '\'' +
                ", currency='" + currency + '\'' +
                ", terminal_id=" + terminal_id +
                ", merchant_order_id=" + merchant_order_id +
                ", items=" + items +
                '}';
    }
}
