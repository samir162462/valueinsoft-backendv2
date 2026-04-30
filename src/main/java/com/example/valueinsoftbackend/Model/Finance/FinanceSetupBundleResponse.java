package com.example.valueinsoftbackend.Model.Finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinanceSetupBundleResponse {
    private int companyId;
    private FinanceSetupOverviewResponse overview;
    private ArrayList<FinanceFiscalYearItem> fiscalYears;
    private ArrayList<FinanceFiscalPeriodItem> fiscalPeriods;
    private ArrayList<FinanceAccountItem> accounts;
    private ArrayList<FinanceAccountMappingItem> accountMappings;
    private ArrayList<FinanceTaxCodeItem> taxCodes;
    private ArrayList<FinanceSupplierItem> suppliers;
    private Instant generatedAt;
}
