package com.example.valueinsoftbackend.Service.inventory;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InventoryLegacyWriterGateTest {

    @Test
    void writersRemainEnabledByDefault() {
        InventoryLegacyWriterGate gate = new InventoryLegacyWriterGate(new InventoryLegacyWriterProperties());

        for (InventoryLegacyWriterGate.Writer writer : InventoryLegacyWriterGate.Writer.values()) {
            assertDoesNotThrow(() -> gate.requireEnabled(writer, 10, 20));
        }
    }

    @Test
    void globalSwitchBlocksWriter() {
        InventoryLegacyWriterProperties properties = new InventoryLegacyWriterProperties();
        properties.getGenericTransaction().setEnabled(false);
        InventoryLegacyWriterGate gate = new InventoryLegacyWriterGate(properties);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> gate.requireEnabled(InventoryLegacyWriterGate.Writer.GENERIC_TRANSACTION, 10, 20)
        );

        assertEquals(HttpStatus.GONE, exception.getStatus());
        assertEquals("INVENTORY_LEGACY_WRITER_DISABLED", exception.getCode());
    }

    @Test
    void exactCanaryScopeBlocksOnlyConfiguredCompanyAndBranch() {
        InventoryLegacyWriterProperties properties = new InventoryLegacyWriterProperties();
        properties.getDamageHardDelete().setDisabledScopes(Set.of("10:20"));
        InventoryLegacyWriterGate gate = new InventoryLegacyWriterGate(properties);

        assertThrows(
                ApiException.class,
                () -> gate.requireEnabled(InventoryLegacyWriterGate.Writer.DAMAGE_HARD_DELETE, 10, 20)
        );
        assertDoesNotThrow(
                () -> gate.requireEnabled(InventoryLegacyWriterGate.Writer.DAMAGE_HARD_DELETE, 10, 21)
        );
        assertDoesNotThrow(
                () -> gate.requireEnabled(InventoryLegacyWriterGate.Writer.DAMAGE_HARD_DELETE, 11, 20)
        );
    }
}
