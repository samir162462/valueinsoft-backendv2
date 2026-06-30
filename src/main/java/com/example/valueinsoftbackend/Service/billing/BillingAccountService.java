package com.example.valueinsoftbackend.Service.billing;

import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import com.example.valueinsoftbackend.Model.Company;
import org.springframework.stereotype.Service;

@Service
public class BillingAccountService {

    private final DbBillingWriteModels dbBillingWriteModels;

    public BillingAccountService(DbBillingWriteModels dbBillingWriteModels) {
        this.dbBillingWriteModels = dbBillingWriteModels;
    }

    public long ensureBillingAccount(Company company) {
        String currencyCode = normalizeCurrency(company.getCurrency());
        Long existingId = dbBillingWriteModels.findBillingAccountIdByCompanyIdAndCurrency(company.getCompanyId(), currencyCode);
        if (existingId != null) {
            return existingId;
        }

        return dbBillingWriteModels.createBillingAccount(
                company.getCompanyId(),
                company.getCompanyId(),
                "TENANT-" + company.getCompanyId(),
                currencyCode,
                company.getCompanyName(),
                "{\"source\":\"legacy_company_subscription\"}"
        );
    }

    private String normalizeCurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            return "EGP";
        }
        String normalized = currencyCode.trim();
        if ("le".equalsIgnoreCase(normalized) || "egp".equalsIgnoreCase(normalized)) {
            return "EGP";
        }
        return normalized.toUpperCase();
    }
}
