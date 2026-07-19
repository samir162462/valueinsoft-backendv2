package com.example.valueinsoftbackend.Model.Configuration.AccessMap;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

/**
 * Graph-ready projection of one tenant user's access across the full capability
 * catalog, resolved for a single (tenant, branch) context.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserAccessMapResponse {
    private AccessMapUserSummary user;
    private int tenantId;
    private Integer activeBranchId;
    /** True when the acting administrator holds users.account.edit. */
    private boolean canMutate;
    private AccessMapSummary summary;
    private ArrayList<AccessMapNode> nodes;
    private ArrayList<AccessMapEdge> edges;
}
