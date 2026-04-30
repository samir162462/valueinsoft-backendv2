package com.example.valueinsoftbackend.Model.Response;

import java.math.BigDecimal;

public class SupplierAgingBucketResponse {

    private final BigDecimal current;
    private final BigDecimal days1To30;
    private final BigDecimal days31To60;
    private final BigDecimal days61To90;
    private final BigDecimal over90;
    private final BigDecimal total;

    public SupplierAgingBucketResponse(BigDecimal current,
                                       BigDecimal days1To30,
                                       BigDecimal days31To60,
                                       BigDecimal days61To90,
                                       BigDecimal over90,
                                       BigDecimal total) {
        this.current = current;
        this.days1To30 = days1To30;
        this.days31To60 = days31To60;
        this.days61To90 = days61To90;
        this.over90 = over90;
        this.total = total;
    }

    public BigDecimal getCurrent() {
        return current;
    }

    public BigDecimal getDays1To30() {
        return days1To30;
    }

    public BigDecimal getDays31To60() {
        return days31To60;
    }

    public BigDecimal getDays61To90() {
        return days61To90;
    }

    public BigDecimal getOver90() {
        return over90;
    }

    public BigDecimal getTotal() {
        return total;
    }
}
