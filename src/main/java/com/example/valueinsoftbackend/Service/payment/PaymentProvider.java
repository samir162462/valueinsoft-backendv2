package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.Model.Request.PaymentTokenRequest;
import com.example.valueinsoftbackend.Model.Request.PayMobTransactionCallbackRequest;
import com.example.valueinsoftbackend.OnlinePayment.OPModel.TransactionProcessedCallback;

import java.math.BigDecimal;

public interface PaymentProvider {

    String getProviderCode();

    int createProviderOrder(int merchantOrderId, int branchId, BigDecimal amountToPay);

    String createPaymentKeyUrl(PaymentTokenRequest request);

    TransactionProcessedCallback parseTransactionCallback(PayMobTransactionCallbackRequest request);
}
