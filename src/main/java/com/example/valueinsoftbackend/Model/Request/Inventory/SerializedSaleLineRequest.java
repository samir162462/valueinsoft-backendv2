package com.example.valueinsoftbackend.Model.Request.Inventory;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SerializedSaleLineRequest {
    @Positive
    private long productId;

    @Size(max = 100)
    private List<@Positive Long> productUnitIds;

    @Size(max = 100)
    private List<@Size(max = 100) String> unitIdentifiers;
}
