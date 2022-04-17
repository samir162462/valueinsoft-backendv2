/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.OnlinePayment.OPModel;

public class TransactionProcessedCallback {

    int order_id;
    boolean pending;
    int amount_cents;
    boolean success;
    boolean is_auth;
    boolean is_capture;
    boolean is_standalois_capturene_payment;
    boolean is_voided;
    boolean is_refunded;
    int subId;


    public TransactionProcessedCallback(int order_id, boolean pending, int amount_cents, boolean success, boolean is_auth, boolean is_capture, boolean is_standalois_capturene_payment, boolean is_voided, boolean is_refunded, int subId) {
        this.order_id = order_id;
        this.pending = pending;
        this.amount_cents = amount_cents;
        this.success = success;
        this.is_auth = is_auth;
        this.is_capture = is_capture;
        this.is_standalois_capturene_payment = is_standalois_capturene_payment;
        this.is_voided = is_voided;
        this.is_refunded = is_refunded;
        this.subId = subId;
    }

    public int getOrder_id() {
        return order_id;
    }

    public void setOrder_id(int order_id) {
        this.order_id = order_id;
    }

    public boolean isPending() {
        return pending;
    }

    public void setPending(boolean pending) {
        this.pending = pending;
    }

    public int getAmount_cents() {
        return amount_cents;
    }

    public void setAmount_cents(int amount_cents) {
        this.amount_cents = amount_cents;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public boolean isIs_auth() {
        return is_auth;
    }

    public void setIs_auth(boolean is_auth) {
        this.is_auth = is_auth;
    }

    public boolean isIs_capture() {
        return is_capture;
    }

    public void setIs_capture(boolean is_capture) {
        this.is_capture = is_capture;
    }

    public boolean isIs_standalois_capturene_payment() {
        return is_standalois_capturene_payment;
    }

    public void setIs_standalois_capturene_payment(boolean is_standalois_capturene_payment) {
        this.is_standalois_capturene_payment = is_standalois_capturene_payment;
    }

    public boolean isIs_voided() {
        return is_voided;
    }

    public void setIs_voided(boolean is_voided) {
        this.is_voided = is_voided;
    }

    public boolean isIs_refunded() {
        return is_refunded;
    }

    public void setIs_refunded(boolean is_refunded) {
        this.is_refunded = is_refunded;
    }

    public int getSubId() {
        return subId;
    }

    public void setSubId(int subId) {
        this.subId = subId;
    }

    @Override
    public String toString() {
        return "TransactionProcessedCallback{" +
                "order_id=" + order_id +
                ", pending=" + pending +
                ", amount_cents=" + amount_cents +
                ", success=" + success +
                ", is_auth=" + is_auth +
                ", is_capture=" + is_capture +
                ", is_standalois_capturene_payment=" + is_standalois_capturene_payment +
                ", is_voided=" + is_voided +
                ", is_refunded=" + is_refunded +
                ", subId=" + subId +
                '}';
    }
}
