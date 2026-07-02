package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Request.PaymentTokenRequest;
import com.example.valueinsoftbackend.Model.Request.PayMobTransactionCallbackRequest;
import com.example.valueinsoftbackend.OnlinePayment.OPModel.TransactionProcessedCallback;
import com.example.valueinsoftbackend.Service.payment.PaymentProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PayMobPaymentIntentionProvider implements PaymentProvider {

    @Override
    public String getProviderCode() {
        return "paymob_intention";
    }

    @Override
    public int createProviderOrder(int merchantOrderId, int branchId, BigDecimal amountToPay) {
        throw unsupported();
    }

    @Override
    public String createPaymentKeyUrl(PaymentTokenRequest request) {
        throw unsupported();
    }

    @Override
    public TransactionProcessedCallback parseTransactionCallback(PayMobTransactionCallbackRequest request) {
        throw unsupported();
    }

    private ApiException unsupported() {
        return new ApiException(
                HttpStatus.NOT_IMPLEMENTED,
                "PAYMOB_INTENTION_CONTRACT_UNCONFIRMED",
                "Paymob Payment Intention mode is blocked until the official request, response, webhook, and HMAC contract is confirmed"
        );
    }
}
