package com.example.valueinsoftbackend.Controller.DataVisualizationControllers;

import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.DatabaseRequests.DbUsers;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Model.Request.DashboardSummaryRequest;
import com.example.valueinsoftbackend.Model.Response.DashboardSummaryResponse;
import com.example.valueinsoftbackend.Model.User;
import com.example.valueinsoftbackend.Service.AuthorizationService;
import com.example.valueinsoftbackend.Service.DashboardSummaryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@Validated
@RequestMapping("/api/dashboard")
public class DashboardSummaryController {

    private final DashboardSummaryService dashboardSummaryService;
    private final AuthorizationService authorizationService;
    private final DbUsers dbUsers;
    private final DbCompany dbCompany;

    public DashboardSummaryController(DashboardSummaryService dashboardSummaryService,
                                      AuthorizationService authorizationService,
                                      DbUsers dbUsers,
                                      DbCompany dbCompany) {
        this.dashboardSummaryService = dashboardSummaryService;
        this.authorizationService = authorizationService;
        this.dbUsers = dbUsers;
        this.dbCompany = dbCompany;
    }

    @PostMapping("/branch-summary")
    public DashboardSummaryResponse getBranchSummary(@Valid @RequestBody DashboardSummaryRequest request,
                                                     Principal principal) {
        
        Integer companyId = resolveCompanyId(principal);
        
        // Validate user has access to this branch and capability
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                request.getBranchId(),
                "dashboard.home.view"
        );

        return dashboardSummaryService.getBranchSummary(companyId, request);
    }

    private Integer resolveCompanyId(Principal principal) {
        String userName = extractBaseUserName(principal.getName());
        User user = dbUsers.getUser(userName);
        if (user == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found");
        }

        if (user.getBranchId() > 0) {
            Company company = dbCompany.getCompanyAndBranchesByUserName(userName);
            if (company != null) {
                return company.getCompanyId();
            }
        }

        Company company = dbCompany.getCompanyByOwnerId(user.getUserId());
        if (company != null) {
            return company.getCompanyId();
        }

        throw new ApiException(HttpStatus.FORBIDDEN, "TENANT_NOT_FOUND", "User has no associated company");
    }

    private String extractBaseUserName(String value) {
        if (value != null && value.contains(" : ")) {
            return value.split(" : ")[0];
        }
        return value == null ? "" : value.trim();
    }
}
