package com.example.valueinsoftbackend.Service.branch;

import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import org.springframework.stereotype.Service;

@Service
public class BranchSubscriptionService {

    private final DbBillingWriteModels dbBillingWriteModels;

    public BranchSubscriptionService(DbBillingWriteModels dbBillingWriteModels) {
        this.dbBillingWriteModels = dbBillingWriteModels;
    }

    public void markPaidByExternalOrderId(String providerCode, String externalOrderId) {
        dbBillingWriteModels.updateBranchSubscriptionStatusByExternalOrderId(
                providerCode,
                externalOrderId,
                "active",
                "{\"source\":\"provider_payment\",\"status\":\"active\"}"
        );
    }
}
