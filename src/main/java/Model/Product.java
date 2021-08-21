package Model;

public class Product {
    int productId;
    String productName;
    String buyingDay;
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


    public Product(int productId, String productName, String buyingDay, String activationPeriod, int rPrice, int lPrice, int bPrice, String companyName, String type, String ownerName, String serial, String desc, int batteryLife, String ownerPhone, String ownerNI, int quantity, String pState) {
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
    }

    public int getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public String getBuyingDay() {
        return buyingDay;
    }

    public String getActivationPeriod() {
        return activationPeriod;
    }

    public int getrPrice() {
        return rPrice;
    }

    public int getlPrice() {
        return lPrice;
    }

    public int getbPrice() {
        return bPrice;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getType() {
        return type;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getSerial() {
        return serial;
    }

    public String getDesc() {
        return desc;
    }

    public int getBatteryLife() {
        return batteryLife;
    }

    public String getOwnerPhone() {
        return ownerPhone;
    }

    public String getOwnerNI() {
        return ownerNI;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getpState() {
        return pState;
    }
}
