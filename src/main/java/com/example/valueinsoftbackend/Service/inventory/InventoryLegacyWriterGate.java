package com.example.valueinsoftbackend.Service.inventory;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class InventoryLegacyWriterGate {

    public enum Writer {
        GENERIC_TRANSACTION,
        STANDALONE_SERIALIZED_STOCK_IN,
        DAMAGE_HARD_DELETE
    }

    private final InventoryLegacyWriterProperties properties;

    public InventoryLegacyWriterGate(InventoryLegacyWriterProperties properties) {
        this.properties = properties;
    }

    public void requireEnabled(Writer writer, long companyId, long branchId) {
        InventoryLegacyWriterProperties.Endpoint endpoint = endpoint(writer);
        String scope = companyId + ":" + branchId;
        if (!endpoint.isEnabled() || endpoint.getDisabledScopes().contains(scope)) {
            throw new ApiException(
                    HttpStatus.GONE,
                    "INVENTORY_LEGACY_WRITER_DISABLED",
                    "Deprecated inventory writer is disabled; use the supported inventory workflow"
            );
        }
    }

    private InventoryLegacyWriterProperties.Endpoint endpoint(Writer writer) {
        return switch (writer) {
            case GENERIC_TRANSACTION -> properties.getGenericTransaction();
            case STANDALONE_SERIALIZED_STOCK_IN -> properties.getStandaloneSerializedStockIn();
            case DAMAGE_HARD_DELETE -> properties.getDamageHardDelete();
        };
    }
}
