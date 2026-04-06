package com.example.valueinsoftbackend.OnlinePayment.OPController;

import com.example.valueinsoftbackend.Model.Request.PaymentTokenRequest;
import com.example.valueinsoftbackend.Model.Request.PayMobTransactionCallbackRequest;
import com.example.valueinsoftbackend.OnlinePayment.OPModel.TransactionProcessedCallback;
import com.example.valueinsoftbackend.Service.PaymentProvider;
import com.example.valueinsoftbackend.Service.PaymentAttemptService;
import com.example.valueinsoftbackend.Service.PaymentProviderResolver;
import com.example.valueinsoftbackend.Service.SubscriptionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@Validated
@RequestMapping("/OP")
public class PayMobController {

    private final PaymentProviderResolver paymentProviderResolver;
    private final PaymentAttemptService paymentAttemptService;
    private final SubscriptionService subscriptionService;

    public PayMobController(PaymentProviderResolver paymentProviderResolver,
                            PaymentAttemptService paymentAttemptService,
                            SubscriptionService subscriptionService) {
        this.paymentProviderResolver = paymentProviderResolver;
        this.paymentAttemptService = paymentAttemptService;
        this.subscriptionService = subscriptionService;
    }

    @RequestMapping(value = "/paymentTKNRequest", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<String> paymentKeyRequest(@Valid @RequestBody PaymentTokenRequest body) {
        PaymentProvider paymentProvider = paymentProviderResolver.getActiveProvider();
        String checkoutUrl = paymentProvider.createPaymentKeyUrl(body);
        paymentAttemptService.markCheckoutRequested(
                paymentProvider.getProviderCode(),
                String.valueOf(body.getOrderId()),
                "{\"checkoutUrl\":\"" + checkoutUrl + "\"}"
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(checkoutUrl);
    }

    @RequestMapping(value = "/TPC", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<TransactionProcessedCallback> transactionProcessedCallbackResp(
            @Valid @RequestBody PayMobTransactionCallbackRequest body
    ) {
        PaymentProvider paymentProvider = paymentProviderResolver.getActiveProvider();
        TransactionProcessedCallback callback = paymentProvider.parseTransactionCallback(body);
        if (callback.isSuccess()) {
            subscriptionService.markBranchSubscriptionStatusSuccess(callback.getSubId());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(callback);
    }
}
