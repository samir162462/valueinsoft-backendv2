package com.example.valueinsoftbackend.Model.Request.Inventory;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SerializedUnitInput {
    @Size(max = 100)
    private String unitIdentifier;

    @Size(max = 32)
    private String imei;

    @Size(max = 100)
    private String serialNumber;

    @Size(max = 30)
    private String conditionCode;
}
