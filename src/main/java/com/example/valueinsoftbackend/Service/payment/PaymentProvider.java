package com.example.valueinsoftbackend.Service.payment;

import com.example.valueinsoftbackend.Model.Request.PaymentTokenRequest;
import com.example.valueinsoftbackend.Model.Request.PayMobTransactionCallbackRequest;
import com.example.valueinsoftbackend.OnlinePayment.OPModel.TransactionProcessedCallback;

import java.math.BigDecimal;
import java.util.List;

public interface PaymentProvider {

    String getProviderCode();

    default List<String> getProviderAliases() {
        return List.of();
    }

    int createProviderOrder(int merchantOrderId, int branchId, BigDecimal amountToPay);

    String createPaymentKeyUrl(PaymentTokenRequest request);

    TransactionProcessedCallback parseTransactionCallback(PayMobTransactionCallbackRequest request);
}
