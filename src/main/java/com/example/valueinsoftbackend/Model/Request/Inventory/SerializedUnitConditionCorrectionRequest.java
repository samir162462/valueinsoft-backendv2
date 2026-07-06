package com.example.valueinsoftbackend.Model.Request.Inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Auditable condition correction for a posted serialized unit.
 * Not a normal edit: a reason is mandatory and the change is recorded in
 * inventory_unit_condition_audit.
 */
@Data
public class SerializedUnitConditionCorrectionRequest {

    @NotBlank(message = "conditionCode is required")
    @Size(max = 10)
    private String conditionCode;

    @NotBlank(message = "reason is required")
    @Size(max = 255)
    private String reason;
}
