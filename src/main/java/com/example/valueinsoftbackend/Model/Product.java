package com.example.valueinsoftbackend.Model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Product {
    @PositiveOrZero(message = "productId must be zero or greater")
    int productId;

    @NotBlank(message = "productName is required")
    @Size(max = 30, message = "productName must be 30 characters or fewer")
    String productName;

    Timestamp buyingDay;

    @Pattern(regexp = "^\\d*$", message = "activationPeriod must contain digits only")
    String activationPeriod;

    @JsonProperty("rPrice")
    @Positive(message = "rPrice must be positive")
    int rPrice;

    @JsonProperty("lPrice")
    @Positive(message = "lPrice must be positive")
    int lPrice;

    @JsonProperty("bPrice")
    @Positive(message = "bPrice must be positive")
    int bPrice;

    @NotBlank(message = "companyName is required")
    @Size(max = 30, message = "companyName must be 30 characters or fewer")
    String companyName;

    @NotBlank(message = "type is required")
    @Size(max = 15, message = "type must be 15 characters or fewer")
    String type;

    @Size(max = 20, message = "ownerName must be 20 characters or fewer")
    String ownerName;

    @Size(max = 35, message = "serial must be 35 characters or fewer")
    String serial;

    @Size(max = 60, message = "desc must be 60 characters or fewer")
    String desc;

    @PositiveOrZero(message = "batteryLife must be zero or greater")
    @Max(value = 100, message = "batteryLife must be 100 or fewer")
    int batteryLife;

    @Size(max = 14, message = "ownerPhone must be 14 characters or fewer")
    String ownerPhone;

    @Size(max = 18, message = "ownerNI must be 18 characters or fewer")
    String ownerNI;

    @PositiveOrZero(message = "quantity must be zero or greater")
    int quantity;

    @JsonProperty("pState")
    @NotBlank(message = "pState is required")
    @Pattern(regexp = "^(New|Used)$", message = "pState must be New or Used")
    String pState;

    @PositiveOrZero(message = "supplierId must be zero or greater")
    int supplierId;

    @NotBlank(message = "major is required")
    @Size(max = 30, message = "major must be 30 characters or fewer")
    String major;

    String image;

    @Size(max = 40, message = "businessLineKey must be 40 characters or fewer")
    String businessLineKey;

    @Size(max = 80, message = "templateKey must be 80 characters or fewer")
    String templateKey;

    @Size(max = 20, message = "baseUomCode must be 20 characters or fewer")
    String baseUomCode;

    @Size(max = 40, message = "pricingPolicyCode must be 40 characters or fewer")
    String pricingPolicyCode;

    String attributes;

    @AssertTrue(message = "rPrice must be greater than or equal to lPrice, and lPrice must be greater than or equal to bPrice")
    public boolean isPriceOrderValid() {
        return rPrice >= lPrice && lPrice >= bPrice;
    }

    @AssertTrue(message = "Used products require ownerName, ownerPhone, activationPeriod, and batteryLife")
    public boolean isUsedProductDetailsValid() {
        if (!"Used".equals(pState)) {
            return true;
        }

        return hasText(ownerName) && hasText(ownerPhone) && hasText(activationPeriod) && batteryLife > 0;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
