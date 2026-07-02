package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Request.PayMobTransactionCallbackRequest;
import com.example.valueinsoftbackend.Model.Request.PaymentTokenRequest;
import com.example.valueinsoftbackend.OnlinePayment.OPModel.TransactionProcessedCallback;
import com.example.valueinsoftbackend.Service.payment.PaymentProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class FawryPayPaymentProvider implements PaymentProvider {

    private final FawryPayService fawryPayService;

    public FawryPayPaymentProvider(FawryPayService fawryPayService) {
        this.fawryPayService = fawryPayService;
    }

    @Override
    public String getProviderCode() {
        return "fawrypay";
    }

    @Override
    public List<String> getProviderAliases() {
        return List.of("fawry", "fawry_pay");
    }

    @Override
    public int createProviderOrder(int merchantOrderId, int branchId, BigDecimal amountToPay) {
        return fawryPayService.createFawryPayOrder(merchantOrderId);
    }

    @Override
    public String createPaymentKeyUrl(PaymentTokenRequest request) {
        return fawryPayService.createCheckoutUrl(request);
    }

    @Override
    public TransactionProcessedCallback parseTransactionCallback(PayMobTransactionCallbackRequest request) {
        throw new ApiException(
                HttpStatus.NOT_IMPLEMENTED,
                "FAWRYPAY_PAYMOB_CALLBACK_UNSUPPORTED",
                "FawryPay does not use the Paymob callback payload"
        );
    }
}
