package com.example.valueinsoftbackend.Controller.Public;

import com.example.valueinsoftbackend.Model.Public.PublicProductDTO;
import com.example.valueinsoftbackend.Model.Public.PublicTenantDTO;
import com.example.valueinsoftbackend.Service.Public.PublicCatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/public/tenants")
public class PublicCatalogController {

    private final PublicCatalogService publicCatalogService;

    public PublicCatalogController(PublicCatalogService publicCatalogService) {
        this.publicCatalogService = publicCatalogService;
    }

    @GetMapping("/{tenantCode}")
    public ResponseEntity<PublicTenantDTO> getTenant(@PathVariable String tenantCode) {
        PublicTenantDTO tenant = publicCatalogService.getTenantInfo(tenantCode);
        return tenant != null ? ResponseEntity.ok(tenant) : ResponseEntity.notFound().build();
    }

    @GetMapping("/{tenantCode}/products")
    public ResponseEntity<List<PublicProductDTO>> getProducts(
            @PathVariable String tenantCode,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(publicCatalogService.getProducts(tenantCode, category, search));
    }

    @GetMapping("/{tenantCode}/products/{productId}")
    public ResponseEntity<PublicProductDTO> getProduct(
            @PathVariable String tenantCode,
            @PathVariable int productId) {
        PublicProductDTO product = publicCatalogService.getProduct(tenantCode, productId);
        return product != null ? ResponseEntity.ok(product) : ResponseEntity.notFound().build();
    }

    @GetMapping("/{tenantCode}/categories")
    public ResponseEntity<List<String>> getCategories(@PathVariable String tenantCode) {
        return ResponseEntity.ok(publicCatalogService.getCategories(tenantCode));
    }
}
