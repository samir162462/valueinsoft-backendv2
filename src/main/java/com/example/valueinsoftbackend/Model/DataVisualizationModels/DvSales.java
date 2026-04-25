package com.example.valueinsoftbackend.Model.DataVisualizationModels;

import java.util.Date;

public class DvSales {
    Date firstDay;
    Date lastDay;
    Date salesDay;
    Integer total;
    Integer income;
    Integer count;

    public DvSales(Date firstDay, Date lastDay, Date salesDay, Integer total, Integer income, Integer count) {
        this.firstDay = firstDay;
        this.lastDay = lastDay;
        this.salesDay = salesDay;
        this.total = total;
        this.income = income;
        this.count = count;
    }

    public DvSales(Integer total, Integer income, Integer count) {
        this.total = total;
        this.income = income;
        this.count = count;
    }

    public Date getFirstDay() { return firstDay; }
    public void setFirstDay(Date firstDay) { this.firstDay = firstDay; }

    public Date getLastDay() { return lastDay; }
    public void setLastDay(Date lastDay) { this.lastDay = lastDay; }

    public Date getSalesDay() { return salesDay; }
    public void setSalesDay(Date salesDay) { this.salesDay = salesDay; }

    public Integer getTotal() { return total; }
    public void setTotal(Integer total) { this.total = total; }

    public Integer getIncome() { return income; }
    public void setIncome(Integer income) { this.income = income; }

    public Integer getCount() { return count; }
    public void setCount(Integer count) { this.count = count; }
}
