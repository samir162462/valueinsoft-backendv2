package com.example.valueinsoftbackend.Model.Request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record OrderItemRequest(
        int itemId,

        @NotBlank(message = "itemName is required")
        String itemName,

        @Positive(message = "quantity must be positive")
        int quantity,

        @Min(value = 0, message = "price must be zero or greater")
        int price,

        @Min(value = 0, message = "total must be zero or greater")
        int total,

        int productId
) {}
