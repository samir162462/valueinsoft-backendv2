package com.example.valueinsoftbackend.Model.ResponseModel.Inventory;

import com.example.valueinsoftbackend.Model.Inventory.ProductUnitStatus;
import com.example.valueinsoftbackend.Model.Inventory.TrackingType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SerializedUnitScanResponse {
    private boolean found;
    private boolean available;
    private String code;
    private String messageCode;
    private String message;
    private Long productUnitId;
    private Long companyId;
    private Long branchId;
    private Long productId;
    private String productName;
    private TrackingType trackingType;
    private String unitIdentifier;
    private String imei;
    private String serialNumber;
    private ProductUnitStatus status;
    private String conditionCode;
}
