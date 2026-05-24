package com.example.valueinsoftbackend.Model.Request.Inventory;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SerializedUnitTransferRequest {
    @Positive
    private long companyId;

    @Positive
    private long fromBranchId;

    @Positive
    private long toBranchId;

    @Positive
    private long productId;

    @NotEmpty
    @Size(max = 100)
    private List<@Positive Long> productUnitIds;

    @Size(max = 100)
    private String actorName;

    @Size(max = 160)
    private String idempotencyKey;
}
