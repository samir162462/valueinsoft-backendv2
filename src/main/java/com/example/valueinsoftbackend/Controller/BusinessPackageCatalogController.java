package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.Configuration.BusinessPackageConfig;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformTenantBusinessPackageAssignmentResponse;
import com.example.valueinsoftbackend.Model.Request.PlatformAdmin.BusinessPackageUpdateRequest;
import com.example.valueinsoftbackend.Model.Request.PlatformAdmin.TenantBusinessPackageAssignmentRequest;
import com.example.valueinsoftbackend.Service.BusinessPackageCatalogService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.ArrayList;

@RestController
@Validated
public class BusinessPackageCatalogController {

    private final BusinessPackageCatalogService businessPackageCatalogService;

    public BusinessPackageCatalogController(BusinessPackageCatalogService businessPackageCatalogService) {
        this.businessPackageCatalogService = businessPackageCatalogService;
    }

    @GetMapping("/public/business-packages")
    public ArrayList<BusinessPackageConfig> getPublicBusinessPackages() {
        return businessPackageCatalogService.getPublicBusinessPackages();
    }

    @GetMapping("/api/platform-admin/business-packages")
    public ArrayList<BusinessPackageConfig> getAdminBusinessPackages(Principal principal) {
        return businessPackageCatalogService.getAdminBusinessPackages(principal.getName());
    }

    @PutMapping("/api/platform-admin/business-packages/{packageId}")
    public BusinessPackageConfig updateBusinessPackage(Principal principal,
                                                       @PathVariable String packageId,
                                                       @Valid @RequestBody BusinessPackageUpdateRequest request) {
        return businessPackageCatalogService.updateBusinessPackage(principal.getName(), packageId, request);
    }

    @GetMapping("/api/platform-admin/companies/{tenantId}/business-package")
    public PlatformTenantBusinessPackageAssignmentResponse getTenantBusinessPackage(Principal principal,
                                                                                    @PathVariable int tenantId) {
        return businessPackageCatalogService.getTenantAssignment(principal.getName(), tenantId);
    }

    @PutMapping("/api/platform-admin/companies/{tenantId}/business-package")
    public PlatformTenantBusinessPackageAssignmentResponse updateTenantBusinessPackage(Principal principal,
                                                                                       @PathVariable int tenantId,
                                                                                       @Valid @RequestBody TenantBusinessPackageAssignmentRequest request) {
        return businessPackageCatalogService.assignTenantBusinessPackage(principal.getName(), tenantId, request);
    }
}
