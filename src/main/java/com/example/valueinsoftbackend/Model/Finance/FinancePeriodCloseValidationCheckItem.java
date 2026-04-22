package com.example.valueinsoftbackend.Model.Finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinancePeriodCloseValidationCheckItem {
    private String code;
    private String severity;
    private String status;
    private String message;
    private long count;
}
