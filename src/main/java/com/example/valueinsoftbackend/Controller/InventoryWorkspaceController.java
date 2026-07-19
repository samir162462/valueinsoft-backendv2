package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryAnalysisResponse;
import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryCatalogBrowseResponse;
import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryPresetResponse;
import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryQuickFindResponse;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryAnalysisRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryCatalogBrowseRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryPresetCreateRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryPresetUpdateRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryQuickFindRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryProductAssignRequest;
import com.example.valueinsoftbackend.Service.inventory.InventoryWorkspaceService;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import com.example.valueinsoftbackend.Service.security.TenantScopeGuard;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.ArrayList;

@RestController
@Validated
@RequestMapping("/api/inventory")
public class InventoryWorkspaceController {

    private final InventoryWorkspaceService inventoryWorkspaceService;
    private final AuthorizationService authorizationService;
    private final TenantScopeGuard tenantScopeGuard;

    public InventoryWorkspaceController(InventoryWorkspaceService inventoryWorkspaceService,
                                        AuthorizationService authorizationService,
                                        TenantScopeGuard tenantScopeGuard) {
        this.inventoryWorkspaceService = inventoryWorkspaceService;
        this.authorizationService = authorizationService;
        this.tenantScopeGuard = tenantScopeGuard;
    }

    @PostMapping("/quick-find")
    public InventoryQuickFindResponse quickFind(Principal principal,
                                                @Valid @RequestBody InventoryQuickFindRequest request) {
        TenantScopeGuard.ResolvedTenantScope scope = authorize(
                principal,
                request.getCompanyId(),
                request.getBranchId(),
                "inventory.item.read"
        );
        InventoryQuickFindResponse response = inventoryWorkspaceService.quickFind(principal.getName(), request);
        if (!canViewCost(principal, scope)) {
            redactCosts(response);
        }
        return response;
    }

    @PostMapping("/catalog/search")
    public InventoryCatalogBrowseResponse browseCatalog(Principal principal,
                                                        @Valid @RequestBody InventoryCatalogBrowseRequest request) {
        TenantScopeGuard.ResolvedTenantScope scope = authorize(
                principal,
                request.getCompanyId(),
                request.getBranchId(),
                "inventory.item.read"
        );
        boolean canViewCost = canViewCost(principal, scope);
        if (!canViewCost && requestsCostData(request)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "INVENTORY_COST_ACCESS_DENIED",
                    "Missing required capability: inventory.pricing.cost.read"
            );
        }
        InventoryCatalogBrowseResponse response = inventoryWorkspaceService.browseCatalog(principal.getName(), request);
        if (!canViewCost && response != null && response.getData() != null) {
            response.getData().forEach(item -> {
                item.setBuyPrice(null);
                item.setUnitCosts(null);
            });
        }
        return response;
    }

    @PostMapping("/analysis/search")
    public InventoryAnalysisResponse analyzeInventory(Principal principal,
                                                      @Valid @RequestBody InventoryAnalysisRequest request) {
        authorize(principal, request.getCompanyId(), request.getBranchId(), "inventory.item.read");
        return inventoryWorkspaceService.analyzeInventory(principal.getName(), request);
    }

    @PostMapping("/products/assign-existing")
    public void assignExistingProductToBranch(Principal principal,
                                              @Valid @RequestBody InventoryProductAssignRequest request) {
        authorize(principal, request.getCompanyId(), request.getBranchId(), "inventory.item.edit");
        inventoryWorkspaceService.assignExistingProductToBranch(principal.getName(), request);
    }

    @GetMapping("/presets")
    public ArrayList<InventoryPresetResponse> getPresets(Principal principal,
                                                         @RequestParam(required = false) Integer companyId,
                                                         @RequestParam(required = false) Integer branchId) {
        TenantScopeGuard.ResolvedTenantScope scope = authorize(
                principal,
                companyId,
                branchId,
                "inventory.item.read"
        );
        return inventoryWorkspaceService.getPresets(principal.getName(), scope.companyId(), scope.branchId());
    }

    @PostMapping("/presets")
    public InventoryPresetResponse createPreset(Principal principal,
                                                @Valid @RequestBody InventoryPresetCreateRequest request) {
        TenantScopeGuard.ResolvedTenantScope scope = authorize(
                principal,
                null,
                request.getBranchId(),
                "inventory.item.read"
        );
        return inventoryWorkspaceService.createPreset(
                principal.getName(),
                scope.companyId(),
                scope.branchId(),
                request
        );
    }

    @PutMapping("/presets/{presetId}")
    public InventoryPresetResponse updatePreset(Principal principal,
                                                @PathVariable String presetId,
                                                @Valid @RequestBody InventoryPresetUpdateRequest request) {
        TenantScopeGuard.ResolvedTenantScope scope = authorize(
                principal,
                null,
                request.getBranchId(),
                "inventory.item.read"
        );
        return inventoryWorkspaceService.updatePreset(
                principal.getName(),
                scope.companyId(),
                presetId,
                request
        );
    }

    @DeleteMapping("/presets/{presetId}")
    public void deletePreset(Principal principal,
                             @PathVariable String presetId) {
        TenantScopeGuard.ResolvedTenantScope scope = authorize(
                principal,
                null,
                null,
                "inventory.item.read"
        );
        inventoryWorkspaceService.deletePreset(principal.getName(), scope.companyId(), presetId);
    }

    private TenantScopeGuard.ResolvedTenantScope authorize(Principal principal,
                                                            Integer companyId,
                                                            Integer branchId,
                                                            String capability) {
        TenantScopeGuard.ResolvedTenantScope scope = tenantScopeGuard.requireScope(
                principal.getName(),
                companyId,
                branchId
        );
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                scope.companyId(),
                scope.branchId(),
                capability
        );
        return scope;
    }

    private boolean canViewCost(Principal principal, TenantScopeGuard.ResolvedTenantScope scope) {
        return authorizationService.hasAuthenticatedCapability(
                principal.getName(),
                scope.companyId(),
                scope.branchId(),
                "inventory.pricing.cost.read"
        );
    }

    private boolean requestsCostData(InventoryCatalogBrowseRequest request) {
        boolean costFilterRequested = request.getFilters() != null
                && (request.getFilters().getBuyPriceMin() != null
                || request.getFilters().getBuyPriceMax() != null);
        String sortField = request.getSort() == null ? null : request.getSort().getField();
        boolean costSortRequested = sortField != null
                && ("buyPrice".equalsIgnoreCase(sortField)
                || "buyingPrice".equalsIgnoreCase(sortField)
                || "cost".equalsIgnoreCase(sortField));
        return costFilterRequested || costSortRequested;
    }

    private void redactCosts(InventoryQuickFindResponse response) {
        if (response == null) {
            return;
        }
        if (response.getExactMatch() != null && response.getExactMatch().getProduct() != null) {
            response.getExactMatch().getProduct().setBuyPrice(null);
            response.getExactMatch().getProduct().setUnitCosts(null);
        }
        if (response.getFallbackMatches() != null) {
            response.getFallbackMatches().forEach(item -> {
                item.setBuyPrice(null);
                item.setUnitCosts(null);
            });
        }
    }
}
