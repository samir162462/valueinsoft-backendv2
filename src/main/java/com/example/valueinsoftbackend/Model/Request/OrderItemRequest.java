package com.example.valueinsoftbackend.Model.Request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

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

        int productId,

        @JsonAlias({"unitIds", "product_unit_ids"})
        @Size(max = 100)
        List<@Positive Long> productUnitIds,

        @JsonAlias({"unit_identifiers"})
        @Size(max = 100)
        List<@Size(max = 100) String> unitIdentifiers,

        @JsonAlias({"imei", "serialNumber", "unitIdentifier"})
        @Size(max = 100)
        String serial
) {
    public OrderItemRequest {
        if (productUnitIds == null) {
            productUnitIds = List.of();
        }
        if (unitIdentifiers == null) {
            unitIdentifiers = List.of();
        }
    }
}
