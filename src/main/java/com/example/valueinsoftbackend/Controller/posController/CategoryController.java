package com.example.valueinsoftbackend.Controller.posController;

import com.example.valueinsoftbackend.Model.MainMajor;
import com.example.valueinsoftbackend.Model.Configuration.BusinessPackageConfig;
import com.example.valueinsoftbackend.Model.Request.SaveCategoryRequest;
import com.example.valueinsoftbackend.Service.AuthorizationService;
import com.example.valueinsoftbackend.Service.BusinessPackageCatalogService;
import com.example.valueinsoftbackend.Service.CategoryService;
import com.example.valueinsoftbackend.util.CustomPair;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.security.Principal;
import java.util.ArrayList;

@RestController
@Validated
@RequestMapping("/Categories")
public class CategoryController {

    private final CategoryService categoryService;
    private final AuthorizationService authorizationService;
    private final BusinessPackageCatalogService businessPackageCatalogService;

    public CategoryController(CategoryService categoryService,
                              AuthorizationService authorizationService,
                              BusinessPackageCatalogService businessPackageCatalogService) {
        this.categoryService = categoryService;
        this.authorizationService = authorizationService;
        this.businessPackageCatalogService = businessPackageCatalogService;
    }

    @PostMapping("/{companyId}/{branchId}/saveCategory")
    ResponseEntity<String> newCategory(
            @Valid @RequestBody SaveCategoryRequest request,
            @PathVariable @Positive int branchId,
            @PathVariable @Positive int companyId,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "inventory.item.create"
        );
        return categoryService.saveCategory(companyId, branchId, request);
    }

    @GetMapping("/getCategoryJson/{companyId}/{branchId}")
    public ArrayList<CustomPair> getCategoriesJson(
            @PathVariable @Positive int branchId,
            @PathVariable @Positive int companyId,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "inventory.item.read"
        );
        return categoryService.getCategoriesJson(companyId, branchId);
    }

    @GetMapping("/getCategoryJsonFlat/{companyId}/{branchId}")
    public String getCategoriesJsonObjectsString(
            @PathVariable @Positive int branchId,
            @PathVariable @Positive int companyId,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "inventory.item.read"
        );
        return categoryService.getCategoriesJsonFlat(companyId, branchId);
    }

    @GetMapping("/getMainMajors/{companyId}")
    public ArrayList<MainMajor> getMainCategories(@PathVariable @Positive int companyId,
                                                  Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                null,
                "inventory.item.read"
        );
        return categoryService.getMainCategories(companyId);
    }

    @GetMapping("/business-package/{companyId}")
    public BusinessPackageConfig getAssignedBusinessPackage(@PathVariable @Positive int companyId,
                                                            Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                null,
                "inventory.item.read"
        );
        return businessPackageCatalogService.getAssignedBusinessPackageForTenant(companyId);
    }
}
