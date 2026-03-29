package com.example.valueinsoftbackend.Model.Request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;

@Data
public class PayMobTransactionCallbackRequest {

    @Valid
    @NotNull(message = "obj is required")
    @JsonProperty("obj")
    private TransactionPayload transaction;

    @Data
    public static class TransactionPayload {

        @NotNull(message = "obj.id is required")
        @Positive(message = "obj.id must be positive")
        private Integer id;

        @NotNull(message = "obj.pending is required")
        private Boolean pending;

        @JsonProperty("amount_cents")
        @NotNull(message = "obj.amount_cents is required")
        @PositiveOrZero(message = "obj.amount_cents must be zero or greater")
        private Integer amountCents;

        @NotNull(message = "obj.success is required")
        private Boolean success;

        @JsonProperty("is_auth")
        @NotNull(message = "obj.is_auth is required")
        private Boolean auth;

        @JsonProperty("is_capture")
        @NotNull(message = "obj.is_capture is required")
        private Boolean capture;

        @JsonProperty("is_standalone_payment")
        @NotNull(message = "obj.is_standalone_payment is required")
        private Boolean standalonePayment;

        @JsonProperty("is_voided")
        @NotNull(message = "obj.is_voided is required")
        private Boolean voided;

        @JsonProperty("is_refunded")
        @NotNull(message = "obj.is_refunded is required")
        private Boolean refunded;

        @Valid
        @NotNull(message = "obj.order is required")
        private OrderPayload order;
    }

    @Data
    public static class OrderPayload {

        @NotNull(message = "obj.order.id is required")
        @Positive(message = "obj.order.id must be positive")
        private Integer id;
    }
}
