package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.Billing.BillingPaymobWebhookSettlementResponse;
import com.example.valueinsoftbackend.Model.Request.PayMobTransactionCallbackRequest;
import com.example.valueinsoftbackend.Service.billing.BillingPaymobWebhookSettlementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BillingPaymobWebhookControllerTest {

    private BillingPaymobWebhookSettlementService settlementService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        settlementService = Mockito.mock(BillingPaymobWebhookSettlementService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new BillingPaymobWebhookController(settlementService)).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void webhookEndpointDelegatesToSettlementService() throws Exception {
        when(settlementService.settleTransactionCallback(any(PayMobTransactionCallbackRequest.class)))
                .thenReturn(new BillingPaymobWebhookSettlementResponse(
                        "SETTLED",
                        "paymob",
                        "777",
                        "123456",
                        900L,
                        31L,
                        41L,
                        42L,
                        false
                ));

        mockMvc.perform(post("/api/billing/paymob/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.providerCode").value("paymob"))
                .andExpect(jsonPath("$.providerEventId").value("777"))
                .andExpect(jsonPath("$.externalOrderId").value("123456"))
                .andExpect(jsonPath("$.duplicate").value(false));
    }

    @Test
    void webhookEndpointNormalizesQueryPayload() throws Exception {
        when(settlementService.settleTransactionCallback(any(PayMobTransactionCallbackRequest.class)))
                .thenReturn(new BillingPaymobWebhookSettlementResponse(
                        "SETTLED",
                        "paymob",
                        "777",
                        "123456",
                        900L,
                        31L,
                        41L,
                        42L,
                        false
                ));

        mockMvc.perform(post("/api/billing/paymob/webhook")
                        .param("hmac", "signed")
                        .param("type", "TRANSACTION")
                        .param("id", "777")
                        .param("pending", "false")
                        .param("amount_cents", "30000")
                        .param("created_at", "2026-07-01T10:15:30Z")
                        .param("currency", "EGP")
                        .param("error_occured", "false")
                        .param("has_parent_transaction", "false")
                        .param("integration_id", "1234")
                        .param("is_3d_secure", "true")
                        .param("success", "true")
                        .param("is_auth", "true")
                        .param("is_capture", "false")
                        .param("is_standalone_payment", "true")
                        .param("is_voided", "false")
                        .param("is_refunded", "false")
                        .param("owner", "55")
                        .param("order", "123456")
                        .param("source_data.pan", "2346")
                        .param("source_data.sub_type", "MasterCard")
                        .param("source_data.type", "card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"));

        ArgumentCaptor<PayMobTransactionCallbackRequest> captor =
                ArgumentCaptor.forClass(PayMobTransactionCallbackRequest.class);
        verify(settlementService).settleTransactionCallback(captor.capture());

        PayMobTransactionCallbackRequest request = captor.getValue();
        assertThat(request.getHmac()).isEqualTo("signed");
        assertThat(request.getType()).isEqualTo("TRANSACTION");
        assertThat(request.getTransaction().getId()).isEqualTo(777);
        assertThat(request.getTransaction().getAmountCents()).isEqualTo(30000);
        assertThat(request.getTransaction().getCurrency()).isEqualTo("EGP");
        assertThat(request.getTransaction().getSuccess()).isTrue();
        assertThat(request.getTransaction().getAuth()).isTrue();
        assertThat(request.getTransaction().getOrder().getId()).isEqualTo(123456);
        assertThat(request.getTransaction().getSourceData().getType()).isEqualTo("card");
        assertThat(request.getTransaction().getSourceData().getSubType()).isEqualTo("MasterCard");
    }

    private PayMobTransactionCallbackRequest request() {
        PayMobTransactionCallbackRequest request = new PayMobTransactionCallbackRequest();
        request.setType("TRANSACTION");
        request.setHmac("signed");

        PayMobTransactionCallbackRequest.OrderPayload order = new PayMobTransactionCallbackRequest.OrderPayload();
        order.setId(123456);

        PayMobTransactionCallbackRequest.TransactionPayload transaction = new PayMobTransactionCallbackRequest.TransactionPayload();
        transaction.setId(777);
        transaction.setPending(false);
        transaction.setAmountCents(30000);
        transaction.setSuccess(true);
        transaction.setAuth(true);
        transaction.setCapture(false);
        transaction.setStandalonePayment(true);
        transaction.setVoided(false);
        transaction.setRefunded(false);
        transaction.setOrder(order);
        request.setTransaction(transaction);
        return request;
    }
}
