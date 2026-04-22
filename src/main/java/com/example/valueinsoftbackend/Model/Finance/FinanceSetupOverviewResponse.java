package com.example.valueinsoftbackend.Model.Finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinanceSetupOverviewResponse {
    private int companyId;
    private int fiscalYearCount;
    private int openFiscalPeriodCount;
    private int accountCount;
    private int activeAccountMappingCount;
    private int activeTaxCodeCount;
    private boolean hasFiscalCalendar;
    private boolean hasChartOfAccounts;
    private boolean hasAccountMappings;
    private boolean hasTaxSetup;
    private boolean setupReadyForPosting;
    private Instant generatedAt;
}
