/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Model;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public class ProductFilter {
    private boolean outOfStock;
    private boolean bouncedBack;
    private boolean used;
    private boolean toSell;

    @PositiveOrZero(message = "rangeMin must be zero or greater")
    private int rangeMin;

    @PositiveOrZero(message = "rangeMax must be zero or greater")
    private int rangeMax;

    @Size(max = 30, message = "major must be 30 characters or fewer")
    private String major;

    @Size(max = 60, message = "dates must be 60 characters or fewer")
    private String dates;

    public ProductFilter() {
    }


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

    @Deprecated
    public String sqlString() {
        StringBuilder stringBuilder = new StringBuilder();
        if (outOfStock && toSell) {
            stringBuilder.append("quantity >= 0 And ");
        } else if (!outOfStock && toSell) {
            stringBuilder.append("quantity > 0 And ");
        } else if (outOfStock) {
            stringBuilder.append("quantity = 0 And ");
        }

        if (rangeMin > 0 || rangeMax < 100000) {
            stringBuilder.append("\"rPrice\" between ").append(rangeMin).append(" And ").append(rangeMax).append(" And ");
        }
        if (used) {
            stringBuilder.append("\"pState\" = 'Used' And ");
        }
        if (major != null && !major.isBlank()) {
            stringBuilder.append("\"major\" = '").append(major).append("' And ");
        }
        if (dates != null && !dates.isBlank()) {
            stringBuilder.append("\"buyingDay\" between ").append(dates).append(" And ");
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
        this.dates = dates;
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

    @AssertTrue(message = "rangeMax must be greater than or equal to rangeMin")
    public boolean isPriceRangeValid() {
        return rangeMax >= rangeMin;
    }
}
