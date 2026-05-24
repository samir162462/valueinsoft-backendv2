package com.example.valueinsoftbackend.Model.Inventory;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductUnit {
    private long productUnitId;
    private long companyId;
    private long branchId;
    private long productId;
    private TrackingType trackingType;
    private String unitIdentifier;
    private String imei;
    private String serialNumber;
    private ProductUnitStatus status;
    private String conditionCode;
    private Long supplierId;
    private String purchaseReferenceType;
    private String purchaseReferenceId;
    private Long purchaseLineId;
    private Long saleOrderId;
    private Long saleOrderDetailId;
    private Long customerId;
    private Long currentTransferId;
    private Timestamp receivedAt;
    private Timestamp soldAt;
    private Timestamp returnedAt;
    private Timestamp statusUpdatedAt;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private long version;
}
