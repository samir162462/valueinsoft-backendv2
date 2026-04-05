package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformCompanySubscriptionItem {
    private int subscriptionId;
    private int branchId;
    private String branchName;
    private Date startTime;
    private Date endTime;
    private BigDecimal amountToPay;
    private BigDecimal amountPaid;
    private int orderId;
    private String status;
}
