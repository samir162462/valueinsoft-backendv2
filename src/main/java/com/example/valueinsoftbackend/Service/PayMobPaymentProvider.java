package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.Model.Request.PaymentTokenRequest;
import com.example.valueinsoftbackend.Model.Request.PayMobTransactionCallbackRequest;
import com.example.valueinsoftbackend.OnlinePayment.OPModel.TransactionProcessedCallback;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PayMobPaymentProvider implements PaymentProvider {

    private final PayMobService payMobService;

    public PayMobPaymentProvider(PayMobService payMobService) {
        this.payMobService = payMobService;
    }

    @Override
    public String getProviderCode() {
        return "paymob";
    }

    @Override
    public int createProviderOrder(int merchantOrderId, int branchId, BigDecimal amountToPay) {
        return payMobService.createPayMobOrder(merchantOrderId, branchId, amountToPay);
    }

    @Override
    public String createPaymentKeyUrl(PaymentTokenRequest request) {
        return payMobService.createPaymentKeyUrl(request);
    }

    @Override
    public TransactionProcessedCallback parseTransactionCallback(PayMobTransactionCallbackRequest request) {
        return payMobService.parseCallback(request);
    }
}
