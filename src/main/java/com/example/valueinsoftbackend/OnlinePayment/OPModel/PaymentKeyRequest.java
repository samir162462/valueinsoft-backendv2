/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.OnlinePayment.OPModel;

public class PaymentKeyRequest {
    String auth_token;
    String amount_cents;
    int  expiration;
    String order_id;
    Billing_data billing_data;
    String currency;
    int integration_id;
    String lock_order_when_paid;


    public PaymentKeyRequest(String auth_token, String amount_cents, int expiration, String order_id, Billing_data billing_data, String currency, int integration_id, String lock_order_when_paid) {
        this.auth_token = auth_token;
        this.amount_cents = amount_cents;
        this.expiration = expiration;
        this.order_id = order_id;
        this.billing_data = billing_data;
        this.currency = currency;
        this.integration_id = integration_id;
        this.lock_order_when_paid = lock_order_when_paid;
    }

    public String getAuth_token() {
        return auth_token;
    }

    public void setAuth_token(String auth_token) {
        this.auth_token = auth_token;
    }

    public String getAmount_cents() {
        return amount_cents;
    }

    public void setAmount_cents(String amount_cents) {
        this.amount_cents = amount_cents;
    }

    public int getExpiration() {
        return expiration;
    }

    public void setExpiration(int expiration) {
        this.expiration = expiration;
    }

    public String getOrder_id() {
        return order_id;
    }

    public void setOrder_id(String order_id) {
        this.order_id = order_id;
    }

    public Billing_data getBilling_data() {
        return billing_data;
    }

    public void setBilling_data(Billing_data billing_data) {
        this.billing_data = billing_data;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public int getIntegration_id() {
        return integration_id;
    }

    public void setIntegration_id(int integration_id) {
        this.integration_id = integration_id;
    }

    public String getLock_order_when_paid() {
        return lock_order_when_paid;
    }

    public void setLock_order_when_paid(String lock_order_when_paid) {
        this.lock_order_when_paid = lock_order_when_paid;
    }

    @Override
    public String toString() {
        return "PaymentKeyRequest{" +
                "auth_token='" + auth_token + '\'' +
                ", amount_cents='" + amount_cents + '\'' +
                ", expiration=" + expiration +
                ", order_id='" + order_id + '\'' +
                ", billing_data=" + billing_data +
                ", currency='" + currency + '\'' +
                ", integration_id=" + integration_id +
                ", lock_order_when_paid='" + lock_order_when_paid + '\'' +
                '}';
    }
}

