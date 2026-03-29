package com.example.valueinsoftbackend.OnlinePayment.OPController;

import com.example.valueinsoftbackend.Model.Request.PaymentTokenRequest;
import com.example.valueinsoftbackend.OnlinePayment.OPModel.TransactionProcessedCallback;
import com.example.valueinsoftbackend.Service.PayMobService;
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

    private final PayMobService payMobService;
    private final SubscriptionService subscriptionService;

    public PayMobController(PayMobService payMobService, SubscriptionService subscriptionService) {
        this.payMobService = payMobService;
        this.subscriptionService = subscriptionService;
    }

    @RequestMapping(value = "/paymentTKNRequest", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<String> paymentKeyRequest(@Valid @RequestBody PaymentTokenRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(payMobService.createPaymentKeyUrl(body));
    }

    @RequestMapping(value = "/TPC", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<TransactionProcessedCallback> transactionProcessedCallbackResp(@RequestBody String body) {
        TransactionProcessedCallback callback = payMobService.parseCallback(body);
        if (callback.isSuccess()) {
            subscriptionService.markBranchSubscriptionStatusSuccess(callback.getSubId());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(callback);
    }
}
