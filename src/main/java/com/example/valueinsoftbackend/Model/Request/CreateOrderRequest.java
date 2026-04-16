package com.example.valueinsoftbackend.Model.Request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
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
        List<OrderItemRequest> orderDetails
) {
    public CreateOrderRequest {
        if (orderDetails == null) {
            orderDetails = List.of();
        }
    }
}
