package com.example.valueinsoftbackend.Model.Request.Inventory;

import com.example.valueinsoftbackend.Model.Inventory.TrackingType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SerializedUnitStockInRequest {
    @Positive
    private long companyId;

    @Positive
    private long branchId;

    @Positive
    private long productId;

    @NotNull
    private TrackingType trackingType;

    private Long supplierId;

    @Size(max = 40)
    private String purchaseReferenceType;

    @Size(max = 64)
    private String purchaseReferenceId;

    private Long purchaseLineId;

    @Size(max = 100)
    private String actorName;

    @Size(max = 160)
    private String idempotencyKey;

    @Valid
    @NotEmpty
    private List<SerializedUnitInput> units;
}
