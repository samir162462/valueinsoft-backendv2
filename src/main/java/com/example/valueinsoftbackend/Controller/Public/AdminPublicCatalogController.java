package com.example.valueinsoftbackend.Controller.Public;

import com.example.valueinsoftbackend.Model.Public.PublicTenantDTO;
import com.example.valueinsoftbackend.Service.AuthorizationService;
import com.example.valueinsoftbackend.Service.Public.PublicCatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/admin/public-catalog")
public class AdminPublicCatalogController {

    private final PublicCatalogService publicCatalogService;
    private final AuthorizationService authorizationService;

    public AdminPublicCatalogController(PublicCatalogService publicCatalogService,
            AuthorizationService authorizationService) {
        this.publicCatalogService = publicCatalogService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/{companyId}")
    public ResponseEntity<PublicTenantDTO> getSettings(@PathVariable int companyId, Principal principal) {
        authorizationService.assertAuthenticatedCapability(principal.getName(), companyId, null,
                "company.settings.read");
        PublicTenantDTO settings = publicCatalogService.getTenantInfoById(companyId);
        return settings != null ? ResponseEntity.ok(settings) : ResponseEntity.ok(new PublicTenantDTO());
    }

    @PostMapping("/{companyId}")
    public ResponseEntity<String> updateSettings(@PathVariable int companyId,
            @RequestBody PublicTenantDTO dto,
            Principal principal) {
        authorizationService.assertAuthenticatedCapability(principal.getName(), companyId, null,
                "company.settings.edit");
        publicCatalogService.updateTenantInfo(companyId, dto);
        return ResponseEntity.ok("Public catalog settings updated successfully");
    }
}
