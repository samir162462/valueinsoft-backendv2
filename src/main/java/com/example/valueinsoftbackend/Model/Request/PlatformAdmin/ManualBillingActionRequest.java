package com.example.valueinsoftbackend.Model.Request.PlatformAdmin;

import lombok.Data;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Data
public class ManualBillingActionRequest {
    @Pattern(regexp = "cash|instapay", flags = Pattern.Flag.CASE_INSENSITIVE, message = "paymentMethod must be cash or instapay")
    private String paymentMethod;

    @DecimalMin(value = "0.01", message = "amount must be greater than zero")
    private BigDecimal amount;

    @Size(max = 255, message = "reference must be 255 characters or less")
    private String reference;

    @Size(max = 1000, message = "note must be 1000 characters or less")
    private String note;
}
