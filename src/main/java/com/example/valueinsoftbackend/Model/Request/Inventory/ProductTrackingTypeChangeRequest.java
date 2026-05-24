package com.example.valueinsoftbackend.Model.Request.Inventory;

import com.example.valueinsoftbackend.Model.Inventory.TrackingType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductTrackingTypeChangeRequest {
    @Positive
    private long companyId;

    @Positive
    private long branchId;

    @Positive
    private long productId;

    @NotNull
    private TrackingType trackingType;

    @Size(max = 100)
    private String sku;

    @Size(max = 100)
    private String barcode;

    @Size(max = 255)
    private String reason;
}
