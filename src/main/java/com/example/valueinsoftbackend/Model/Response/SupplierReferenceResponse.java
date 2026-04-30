package com.example.valueinsoftbackend.Model.Response;

import java.math.BigDecimal;
import java.util.Map;

public class SupplierReferenceResponse {

    private final int supplierId;
    private final boolean canDelete;
    private final boolean canArchive;
    private final Map<String, Long> references;
    private final BigDecimal openBalance;

    public SupplierReferenceResponse(int supplierId,
                                     boolean canDelete,
                                     boolean canArchive,
                                     Map<String, Long> references,
                                     BigDecimal openBalance) {
        this.supplierId = supplierId;
        this.canDelete = canDelete;
        this.canArchive = canArchive;
        this.references = references;
        this.openBalance = openBalance;
    }

    public int getSupplierId() {
        return supplierId;
    }

    public boolean isCanDelete() {
        return canDelete;
    }

    public boolean isCanArchive() {
        return canArchive;
    }

    public Map<String, Long> getReferences() {
        return references;
    }

    public BigDecimal getOpenBalance() {
        return openBalance;
    }
}
