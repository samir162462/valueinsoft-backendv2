package com.example.valueinsoftbackend.Model.Finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyCashClosingHeader {
    private String companyName;
    private String branchName;
    private String cashierName;
    private String shiftLabel;
}
