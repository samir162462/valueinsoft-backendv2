package com.example.valueinsoftbackend.pos.offline.dto.response;

public record OfflineAdminOnlineOrderReference(
        Long postedOrderId,
        Long officialOrderId,
        Long branchId,
        String orderTableReference
) {
}
