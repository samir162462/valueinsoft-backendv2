package com.example.valueinsoftbackend.Model.Request.Finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyCashClosingReportRequest {
    private int companyId;
    private int branchId;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private String cashierId;
    private Integer shiftId;
    private String paymentMethod;
    private String status;
}
