/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Model;

public class ProductFilter {
    boolean outOfStock;
    boolean bouncedBack;
    boolean used;
    boolean toSell;
    int rangeMin;
    int rangeMax;
    String major;
    String dates;


    public ProductFilter(boolean outOfStock, boolean bouncedBack, boolean used, boolean toSell, int rangeMin, int rangeMax, String major, String dates) {
        this.outOfStock = outOfStock;
        this.bouncedBack = bouncedBack;
        this.used = used;
        this.toSell = toSell;
        this.rangeMin = rangeMin;
        this.rangeMax = rangeMax;
        this.major = major;
        this.dates = dates;
    }

    public String  sqlString()
    {
        String text  = "";
        StringBuilder stringBuilder = new StringBuilder(text);
        if (outOfStock == true &&toSell ==true) {
            stringBuilder.append("quantity >= 0 And ");
        }else if(outOfStock == false &&toSell ==true){
            stringBuilder.append("quantity > 0 And ");

        }else if(outOfStock == true && !toSell){
            stringBuilder.append("quantity = 0 And ");

        }

        if (rangeMin > 0 || rangeMax<100000) {
            stringBuilder.append("\"rPrice\" between "+rangeMin+" And "+rangeMax+" And ");

        }
        if (used == true) {
        stringBuilder.append("\"pState\" = 'Used' And ");

        }
        if (major != null && major!="") {
            stringBuilder.append("\"major\" = '"+major+"' And ");

        }
        if (dates != null && dates!="") {
            stringBuilder.append("\"buyingDay\" between "+dates+" And ");

        }


        return stringBuilder.toString();
    }

    public boolean isOutOfStock() {
        return outOfStock;
    }

    public void setOutOfStock(boolean outOfStock) {
        this.outOfStock = outOfStock;
    }

    public boolean isToSell() {
        return toSell;
    }

    public void setToSell(boolean toSell) {
        this.toSell = toSell;
    }

    public String getDates() {
        return dates;
    }

    public void setDates(String dates) {
        dates = dates;
    }

    public boolean isUsed() {
        return used;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }

    public int getRangeMin() {
        return rangeMin;
    }

    public void setRangeMin(int rangeMin) {
        this.rangeMin = rangeMin;
    }

    public int getRangeMax() {
        return rangeMax;
    }

    public void setRangeMax(int rangeMax) {
        this.rangeMax = rangeMax;
    }

    public boolean isBouncedBack() {
        return bouncedBack;
    }

    public void setBouncedBack(boolean bouncedBack) {
        this.bouncedBack = bouncedBack;
    }

    public String getMajor() {
        return major;
    }

    public void setMajor(String major) {
        this.major = major;
    }

    @Override
    public String toString() {
        return "ProductFilter{" +
                "outOfStock=" + outOfStock +
                ", bouncedBack=" + bouncedBack +
                ", used=" + used +
                ", toSell=" + toSell +
                ", rangeMin=" + rangeMin +
                ", rangeMax=" + rangeMax +
                ", major='" + major + '\'' +
                ", dates='" + dates + '\'' +
                '}';
    }
}
