package com.example.valueinsoftbackend.Model.Request.Inventory;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload used to correct the identifier (IMEI or serial number) of an
 * already-registered serialized unit. The tracking type is derived from the
 * stored unit, so callers only provide the relevant identifier value.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SerializedUnitIdentifierUpdateRequest {

    @Size(max = 64, message = "imei must be 64 characters or fewer")
    private String imei;

    @Size(max = 120, message = "serialNumber must be 120 characters or fewer")
    private String serialNumber;

    @Size(max = 40, message = "conditionCode must be 40 characters or fewer")
    private String conditionCode;
}
