package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.Config.BillingProperties;
import com.example.valueinsoftbackend.Model.Request.PaymentTokenRequest;
import com.example.valueinsoftbackend.Model.Request.PayMobTransactionCallbackRequest;
import com.example.valueinsoftbackend.OnlinePayment.OPModel.TransactionProcessedCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
public class MockPaymentProvider implements PaymentProvider {

    private final BillingProperties billingProperties;

    public MockPaymentProvider(BillingProperties billingProperties) {
        this.billingProperties = billingProperties;
    }

    @Override
    public String getProviderCode() {
        return "mock";
    }

    @Override
    public int createProviderOrder(int merchantOrderId, int branchId, BigDecimal amountToPay) {
        int orderId = ThreadLocalRandom.current().nextInt(100000000, 999999999);
        log.info(
                "Created mock payment order {} for merchantOrderId {} branch {} amount {}",
                orderId,
                merchantOrderId,
                branchId,
                amountToPay
        );
        return orderId;
    }

    @Override
    public String createPaymentKeyUrl(PaymentTokenRequest request) {
        return UriComponentsBuilder
                .fromUriString(billingProperties.getMockCheckoutBaseUrl())
                .queryParam("orderId", request.getOrderId())
                .queryParam("branchId", request.getBranchId())
                .queryParam("companyId", request.getCompanyId())
                .queryParam("amountCents", request.getAmountCents())
                .queryParam("currency", request.getCurrency())
                .queryParam("provider", getProviderCode())
                .build()
                .toUriString();
    }

    @Override
    public TransactionProcessedCallback parseTransactionCallback(PayMobTransactionCallbackRequest request) {
        PayMobTransactionCallbackRequest.TransactionPayload transaction = request.getTransaction();
        TransactionProcessedCallback callback = new TransactionProcessedCallback(
                transaction.getId(),
                transaction.getPending(),
                transaction.getAmountCents(),
                transaction.getSuccess(),
                transaction.getAuth(),
                transaction.getCapture(),
                transaction.getStandalonePayment(),
                transaction.getVoided(),
                transaction.getRefunded(),
                transaction.getOrder().getId()
        );
        log.info("Processed mock payment callback order {} success={}", callback.getSubId(), callback.isSuccess());
        return callback;
    }
}
