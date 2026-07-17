package com.example.valueinsoftbackend.Model.Request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(
        @PositiveOrZero(message = "orderId must be zero or greater")
        int orderId,

        String orderTime,

        String clientName,

        @NotBlank(message = "orderType is required")
        String orderType,

        @Min(value = 0, message = "orderDiscount must be zero or greater")
        int orderDiscount,

        @Min(value = 0, message = "orderTotal must be zero or greater")
        int orderTotal,

        @NotBlank(message = "salesUser is required")
        String salesUser,

        @Positive(message = "branchId must be positive")
        int branchId,

        @PositiveOrZero(message = "clientId must be zero or greater")
        int clientId,

        @Min(value = 0, message = "orderIncome must be zero or greater")
        int orderIncome,

        @Valid
        @NotEmpty(message = "orderDetails must contain at least one item")
        List<OrderItemRequest> orderDetails,

        @PositiveOrZero(message = "loyaltyRedemptionId must be zero or greater")
        Long loyaltyRedemptionId,

        @PositiveOrZero(message = "loyaltyPointsRedeemed must be zero or greater")
        Integer loyaltyPointsRedeemed,

        @PositiveOrZero(message = "loyaltyPointsEarned must be zero or greater")
        Integer loyaltyPointsEarned,

        @PositiveOrZero(message = "loyaltyDiscountAmount must be zero or greater")
        BigDecimal loyaltyDiscountAmount,

        @PositiveOrZero(message = "loyaltyNetAmount must be zero or greater")
        BigDecimal loyaltyNetAmount,

        @jakarta.validation.constraints.Pattern(regexp = "CLIENT|SUPPLIER", flags = jakarta.validation.constraints.Pattern.Flag.CASE_INSENSITIVE,
                message = "receivablePartyType must be CLIENT or SUPPLIER")
        String receivablePartyType,

        @PositiveOrZero(message = "receivableSupplierId must be zero or greater")
        Integer receivableSupplierId,

        /**
         * Cash received at checkout when the sale is partly paid and the balance
         * is posted to the selected client's receivable account. Null means the
         * legacy/full-payment flow.
         */
        @PositiveOrZero(message = "paidNowAmount must be zero or greater")
        BigDecimal paidNowAmount,

        /**
         * Optional idempotency token to ensure safe retries without duplicate processing.
         * A UUID format is strongly recommended.
         */
        @jakarta.validation.constraints.Size(max = 255, message = "idempotencyKey length cannot exceed 255")
        String idempotencyKey
) {
    public CreateOrderRequest {
        if (orderDetails == null) {
            orderDetails = List.of();
        }
        if (idempotencyKey != null) {
            idempotencyKey = idempotencyKey.trim();
        }
    }
}
