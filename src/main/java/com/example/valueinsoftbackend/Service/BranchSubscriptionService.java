package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Model.Request.CreateSubscriptionRequest;
import org.springframework.stereotype.Service;

import java.sql.Date;

@Service
public class BranchSubscriptionService {

    private final DbBillingWriteModels dbBillingWriteModels;

    public BranchSubscriptionService(DbBillingWriteModels dbBillingWriteModels) {
        this.dbBillingWriteModels = dbBillingWriteModels;
    }

    public long ensureLegacyMirroredSubscription(long billingAccountId,
                                                 Company company,
                                                 Branch branch,
                                                 int legacySubscriptionId,
                                                 CreateSubscriptionRequest request) {
        Long existingId = dbBillingWriteModels.findBranchSubscriptionIdByLegacySubscriptionId(legacySubscriptionId);
        if (existingId != null) {
            return existingId;
        }

        return dbBillingWriteModels.createBranchSubscription(
                billingAccountId,
                branch.getBranchID(),
                company.getCompanyId(),
                legacySubscriptionId,
                company.getPlan(),
                "pending_payment",
                request.getAmountToPay(),
                Date.valueOf(request.getStartTime()),
                Date.valueOf(request.getStartTime()),
                Date.valueOf(request.getEndTime()),
                "{\"legacyStatus\":\"NP\"}"
        );
    }

    public void markPaidByExternalOrderId(String providerCode, String externalOrderId) {
        dbBillingWriteModels.updateBranchSubscriptionStatusByExternalOrderId(
                providerCode,
                externalOrderId,
                "active",
                "{\"legacyStatus\":\"PD\"}"
        );
    }

    public void markPaidByLegacySubscriptionId(int legacySubscriptionId) {
        dbBillingWriteModels.updateBranchSubscriptionStatusByLegacySubscriptionId(
                legacySubscriptionId,
                "active",
                "{\"legacyStatus\":\"PD\"}"
        );
    }
}
