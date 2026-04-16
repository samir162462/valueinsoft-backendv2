package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformPlanItem;
import com.example.valueinsoftbackend.Model.Request.PlatformAdmin.PlatformPlanUpdateRequest;
import com.example.valueinsoftbackend.Service.PlatformAdminPlanService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.ArrayList;

@RestController
@Validated
public class PlanCatalogController {

    private final PlatformAdminPlanService platformAdminPlanService;

    public PlanCatalogController(PlatformAdminPlanService platformAdminPlanService) {
        this.platformAdminPlanService = platformAdminPlanService;
    }

    @GetMapping("/public/package-plans")
    public ArrayList<PlatformPlanItem> getPublicPlans() {
        return platformAdminPlanService.getPublicActivePlans();
    }

    @GetMapping("/api/platform-admin/plans")
    public ArrayList<PlatformPlanItem> getAdminPlans(Principal principal) {
        return platformAdminPlanService.getPlansForAuthenticatedUser(principal.getName());
    }

    @PutMapping("/api/platform-admin/plans/{packageId}")
    public PlatformPlanItem updateAdminPlan(Principal principal,
                                            @PathVariable String packageId,
                                            @Valid @RequestBody PlatformPlanUpdateRequest request) {
        return platformAdminPlanService.updatePlanForAuthenticatedUser(
                principal.getName(),
                packageId,
                request
        );
    }
}
