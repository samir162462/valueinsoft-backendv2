package com.example.valueinsoftbackend.Model.Request.PlatformAdmin;

import lombok.Data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.ArrayList;

@Data
public class PlatformPlanUpdateRequest {
    @NotBlank
    private String displayName;

    @Pattern(regexp = "active|retired")
    private String status;

    @NotBlank
    private String priceCode;

    private String configVersion;

    @NotBlank
    private String description;

    @DecimalMin("0.00")
    private BigDecimal monthlyPriceAmount;

    @Pattern(regexp = "^[A-Z]{3}$")
    private String currencyCode;

    private int displayOrder;
    private boolean featured;
    private Integer maxUsers;

    @Valid
    private ArrayList<PlatformPlanModuleUpdateRequest> modules = new ArrayList<>();
}
