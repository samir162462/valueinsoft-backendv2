package com.example.valueinsoftbackend.Model;

import java.sql.Timestamp;

public class Product {
    int productId;
    String productName;
    Timestamp buyingDay;
    String activationPeriod;
    int rPrice;
    int lPrice;
    int bPrice;
    String companyName;
    String type;
    String ownerName;
    String serial;
    String desc;
    int batteryLife;
    String ownerPhone;
    String ownerNI;
    int quantity;
    String pState;
    int supplierId;
    String major;
    String image;

    public Product(int productId, String productName, Timestamp buyingDay, String activationPeriod, int rPrice, int lPrice, int bPrice, String companyName, String type, String ownerName, String serial, String desc, int batteryLife, String ownerPhone, String ownerNI, int quantity, String pState, int supplierId, String major, String imgFile) {
        this.productId = productId;
        this.productName = productName;
        this.buyingDay = buyingDay;
        this.activationPeriod = activationPeriod;
        this.rPrice = rPrice;
        this.lPrice = lPrice;
        this.bPrice = bPrice;
        this.companyName = companyName;
        this.type = type;
        this.ownerName = ownerName;
        this.serial = serial;
        this.desc = desc;
        this.batteryLife = batteryLife;
        this.ownerPhone = ownerPhone;
        this.ownerNI = ownerNI;
        this.quantity = quantity;
        this.pState = pState;
        this.supplierId = supplierId;
        this.major = major;
    }

    public String getMajor() {
        return major;
    }

    public void setMajor(String major) {
        this.major = major;
    }

    public int getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(int supplierId) {
        this.supplierId = supplierId;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Timestamp getBuyingDay() {
        return buyingDay;
    }

    public void setBuyingDay(Timestamp buyingDay) {
        this.buyingDay = buyingDay;
    }

    public String getActivationPeriod() {
        return activationPeriod;
    }

    public void setActivationPeriod(String activationPeriod) {
        this.activationPeriod = activationPeriod;
    }

    public int getrPrice() {
        return rPrice;
    }

    public void setrPrice(int rPrice) {
        this.rPrice = rPrice;
    }

    public int getlPrice() {
        return lPrice;
    }

    public void setlPrice(int lPrice) {
        this.lPrice = lPrice;
    }

    public int getbPrice() {
        return bPrice;
    }

    public void setbPrice(int bPrice) {
        this.bPrice = bPrice;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getSerial() {
        return serial;
    }

    public void setSerial(String serial) {
        this.serial = serial;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }


    public int getBatteryLife() {
        return batteryLife;
    }

    public void setBatteryLife(int batteryLife) {
        this.batteryLife = batteryLife;
    }

    public String getOwnerPhone() {
        return ownerPhone;
    }

    public void setOwnerPhone(String ownerPhone) {
        this.ownerPhone = ownerPhone;
    }

    public String getOwnerNI() {
        return ownerNI;
    }

    public void setOwnerNI(String ownerNI) {
        this.ownerNI = ownerNI;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getpState() {
        return pState;
    }

    public void setpState(String pState) {
        this.pState = pState;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    @Override
    public String toString() {
        return "Product{" +
                "productId=" + productId +
                ", productName='" + productName + '\'' +
                ", buyingDay=" + buyingDay +
                ", activationPeriod='" + activationPeriod + '\'' +
                ", rPrice=" + rPrice +
                ", lPrice=" + lPrice +
                ", bPrice=" + bPrice +
                ", companyName='" + companyName + '\'' +
                ", type='" + type + '\'' +
                ", ownerName='" + ownerName + '\'' +
                ", serial='" + serial + '\'' +
                ", desc='" + desc + '\'' +
                ", batteryLife=" + batteryLife +
                ", ownerPhone='" + ownerPhone + '\'' +
                ", ownerNI='" + ownerNI + '\'' +
                ", quantity=" + quantity +
                ", pState='" + pState + '\'' +
                '}';
    }
}
