package com.example.valueinsoftbackend.Service.inventory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Reversible Phase 0 controls for inventory writers scheduled for retirement.
 * A disabled scope is an exact {@code companyId:branchId} pair; do not add
 * tenant identifiers to metric tags.
 */
@Configuration
@ConfigurationProperties(prefix = "vls.inventory.legacy-writers")
public class InventoryLegacyWriterProperties {

    private Endpoint genericTransaction = new Endpoint();
    private Endpoint standaloneSerializedStockIn = new Endpoint();
    private Endpoint damageHardDelete = new Endpoint();

    public Endpoint getGenericTransaction() {
        return genericTransaction;
    }

    public void setGenericTransaction(Endpoint genericTransaction) {
        this.genericTransaction = genericTransaction;
    }

    public Endpoint getStandaloneSerializedStockIn() {
        return standaloneSerializedStockIn;
    }

    public void setStandaloneSerializedStockIn(Endpoint standaloneSerializedStockIn) {
        this.standaloneSerializedStockIn = standaloneSerializedStockIn;
    }

    public Endpoint getDamageHardDelete() {
        return damageHardDelete;
    }

    public void setDamageHardDelete(Endpoint damageHardDelete) {
        this.damageHardDelete = damageHardDelete;
    }

    public static class Endpoint {
        private boolean enabled = true;
        private Set<String> disabledScopes = new LinkedHashSet<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Set<String> getDisabledScopes() {
            return disabledScopes;
        }

        public void setDisabledScopes(Set<String> disabledScopes) {
            this.disabledScopes = disabledScopes == null ? new LinkedHashSet<>() : disabledScopes;
        }
    }
}
