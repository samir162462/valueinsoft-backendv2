package com.example.valueinsoftbackend.Model.Payroll;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayrollPayment {
    private long id;
    private int companyId;
    private long payrollRunId;
    private Date paymentDate;
    private String paymentMethod;
    private BigDecimal totalAmount;
    private String currencyCode;
    private String referenceNumber;
    private String status;            // COMPLETED | CANCELLED
    private UUID postingRequestId;
    private UUID journalId;
    private Timestamp postedAt;
    private String notes;
    private int version;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private String createdBy;
    private String updatedBy;
}
