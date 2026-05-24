package com.example.valueinsoftbackend.Model.Inventory;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductTrackingMetadata {
    private TrackingType trackingType = TrackingType.QUANTITY;
    private String sku;
    private String barcode;
    private Long version;
}
