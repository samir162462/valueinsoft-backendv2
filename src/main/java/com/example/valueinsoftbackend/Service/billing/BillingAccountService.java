package com.example.valueinsoftbackend.Service;

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
        Long existingId = dbBillingWriteModels.findBillingAccountIdByCompanyId(company.getCompanyId());
        if (existingId != null) {
            return existingId;
        }

        String currencyCode = company.getCurrency() == null || company.getCurrency().isBlank()
                ? "EGP"
                : company.getCurrency().trim();
        return dbBillingWriteModels.createBillingAccount(
                company.getCompanyId(),
                company.getCompanyId(),
                "TENANT-" + company.getCompanyId(),
                currencyCode,
                company.getCompanyName(),
                "{\"source\":\"legacy_company_subscription\"}"
        );
    }
}
