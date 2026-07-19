package com.example.valueinsoftbackend.Model.Configuration.AccessMap;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Aggregate counts for the selected user's access map, computed from the full
 * capability catalog (never from visible/expanded graph nodes).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccessMapSummary {
    private int accessibleCapabilities;
    private int totalCapabilities;
    private int directGrants;
    private int roleGrants;
    private int explicitDenies;
    private int lockedCapabilities;
    private int deprecatedCapabilities;
    private int scopeMismatches;
}
