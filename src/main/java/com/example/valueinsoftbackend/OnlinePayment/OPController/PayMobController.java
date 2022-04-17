/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.OnlinePayment.OPController;


import com.example.valueinsoftbackend.DatabaseRequests.DbApp.DbSubscription;
import com.example.valueinsoftbackend.OnlinePayment.OPModel.Billing_data;
import com.example.valueinsoftbackend.OnlinePayment.OPModel.OrderRegistration;
import com.example.valueinsoftbackend.OnlinePayment.OPModel.PaymentKeyRequest;
import com.example.valueinsoftbackend.OnlinePayment.OPModel.TransactionProcessedCallback;
import com.example.valueinsoftbackend.OnlinePayment.PayMobProperties;
import com.example.valueinsoftbackend.ValueinsoftBackendApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


@RestController
@RequestMapping("/OP")
@CrossOrigin("*")
public class PayMobController {

    public static String pMNToken = null;


    public static String createPostAuth() {
        String url = "https://accept.paymob.com/api/auth/tokens";
        RestTemplate restTemplate = new RestTemplate();
        // create headers
        HttpHeaders headers = new HttpHeaders();
        // set `content-type` header
        headers.setContentType(MediaType.APPLICATION_JSON);
        // set `accept` header
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        // create a map for post parameters
        Map<String, Object> map = new HashMap<>();
        map.put("api_key", ValueinsoftBackendApplication.PAYMTOKEN);
        // build the request
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(map, headers);
        // send POST request
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
        // check response status code
        System.out.println(response.getStatusCode());
        if (response.getStatusCode() == HttpStatus.CREATED) {
            System.out.println(response.getBody());
            pMNToken = response.getBody().split(":")[1].split(",")[0].replaceAll("\"", "");
            return response.getBody().split(":")[1].split(",")[0].replaceAll("\"", "");
        } else {
            return null;
        }
    }


    public String getPaymentKeyRequestToken(PaymentKeyRequest paymentKeyRequest) {
        String url = "https://accept.paymob.com/api/acceptance/payment_keys";
        RestTemplate restTemplate = new RestTemplate();
        // create headers
        HttpHeaders headers = new HttpHeaders();
        // set `content-type` header
        headers.setContentType(MediaType.APPLICATION_JSON);
        // set `accept` header
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        // build the request
        // send POST request
        ResponseEntity<String> response = restTemplate.postForEntity(url, paymentKeyRequest, String.class);
        // check response status code
        System.out.println(response.getStatusCode());
        if (response.getStatusCode() == HttpStatus.CREATED) {
            System.out.println(response.getBody());
            return response.getBody().split(":")[1].split(",")[0].replaceAll("\"", "").replaceAll("}", "");
        } else {
            return null;
        }
    }

    @RequestMapping(value = "/paymentTKNRequest", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<String> paymentKeyRequest(@RequestBody Map<String, String> body
    ) {
        pMNToken = createPostAuth();
        String amount = body.get("amount_cents");
        String orderId = body.get("order_id");
        String currency = body.get("currency");
        String companyId = body.get("companyId");
        String branchId = body.get("branchId");
        Billing_data billing_data = new Billing_data(branchId, companyId, branchId + companyId + "@VLS.com", "" + branchId + companyId);
        if (pMNToken != null) {
            PaymentKeyRequest paymentKeyRequest = new PaymentKeyRequest(pMNToken, amount, 3600, orderId, billing_data, currency, PayMobProperties.CardIntegrationId, "false");
            try {
                String RID = getPaymentKeyRequestToken(paymentKeyRequest);
                if (RID != null) {
                    System.out.println("RID " + RID);
                    String url = "https://accept.paymob.com/api/acceptance/iframes/370887?payment_token=" + RID;
                    return ResponseEntity.status(HttpStatus.CREATED).body(url);

                }
            } catch (Exception e) {
                System.out.println(e.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());

            }
        }
        // String id = createOrderRegistrationId();
        // System.out.println(id);
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body("error in logic");
    }

    @RequestMapping(value = "/TPC", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<TransactionProcessedCallback> TransactionProcessedCallbackResp(
            @RequestBody String obj
    ) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(obj);
            TransactionProcessedCallback transactionProcessedCallback  =  new TransactionProcessedCallback(
                    jsonNode.get("obj").get("id").asInt(),
                    jsonNode.get("obj").get("pending").asBoolean(),
                    jsonNode.get("obj").get("amount_cents").asInt(),
                    jsonNode.get("obj").get("success").asBoolean(),
                    jsonNode.get("obj").get("is_auth").asBoolean(),
                    jsonNode.get("obj").get("is_capture").asBoolean(),
                    jsonNode.get("obj").get("is_standalone_payment").asBoolean(),
                    jsonNode.get("obj").get("is_voided").asBoolean(),
                    jsonNode.get("obj").get("is_refunded").asBoolean(),
                    jsonNode.get("obj").get("order").get("id").asInt()
            );
            System.out.println(transactionProcessedCallback.toString());
            if (transactionProcessedCallback.isSuccess()) {
                DbSubscription.updateBranchSubscriptionStatusSuccess(transactionProcessedCallback.getSubId(), transactionProcessedCallback.isSuccess());
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(transactionProcessedCallback);

        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println("error");
            return  ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body(null);

        }
    }


}
