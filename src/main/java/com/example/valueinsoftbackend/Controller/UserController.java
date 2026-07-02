package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.DatabaseRequests.DbUsers;
import com.example.valueinsoftbackend.Service.UserPerformanceService;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Model.Request.AdminResetPasswordRequest;
import com.example.valueinsoftbackend.Model.Request.ResetPasswordRequest;
import com.example.valueinsoftbackend.Model.Request.SaveUserRequest;
import com.example.valueinsoftbackend.Model.Request.UpdateUserImageRequest;
import com.example.valueinsoftbackend.Model.Response.UserPerformanceResponse;
import com.example.valueinsoftbackend.Model.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.ArrayList;

@RestController
@RequestMapping("/users")
@Tag(name = "User Management", description = "Endpoints for user profiles and account management")
public class UserController {

    private final DbUsers dbUsers;
    private final DbCompany dbCompany;
    private final AuthorizationService authorizationService;
    private final UserPerformanceService userPerformanceService;

    public UserController(DbUsers dbUsers, DbCompany dbCompany, AuthorizationService authorizationService,
                          UserPerformanceService userPerformanceService) {
        this.dbUsers = dbUsers;
        this.dbCompany = dbCompany;
        this.authorizationService = authorizationService;
        this.userPerformanceService = userPerformanceService;
    }

    @Operation(summary = "Get user profile by ID", description = "Retrieves the full profile of a user given their numeric ID.")
    @RequestMapping(value = "/getUser", method = RequestMethod.GET)
    @ResponseBody
    public User getPersonsByNames(@RequestParam("id") String id, Principal principal) {
        authorizationService.assertSelfCapability(
                principal.getName(),
                id,
                "profile.self.read"
        );
        User user = dbUsers.getUser(id);
        if (user == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found!");
        }
        return user;
    }

    @RequestMapping(value = "/getUserDetails/{userName}", method = RequestMethod.GET)
    @ResponseBody
    public User getUserByName(@PathVariable("userName") String userName, Principal principal) {
        authorizationService.assertSelfCapability(
                principal.getName(),
                userName,
                "profile.self.read"
        );
        User user = dbUsers.getUserDetails(userName);
        if (user == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found!");
        }
        return user;
    }

    @RequestMapping(value = "/{companyId}/{branchId}/getAllUsers", method = RequestMethod.GET)
    @ResponseBody
    public ArrayList<User> getAllUsers(@PathVariable("branchId") int branchId,
                                       @PathVariable("companyId") int companyId,
                                       Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "users.account.read"
        );
        return (ArrayList<User>) dbUsers.getAllUsers(branchId);
    }

    @RequestMapping(value = "/{companyId}/{branchId}/searchUsers/{name}", method = RequestMethod.GET)
    @ResponseBody
    public ArrayList<User> searchUsers(@PathVariable("branchId") int branchId,
                                       @PathVariable("companyId") int companyId,
                                       @PathVariable("name") String name,
                                       Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "users.account.read"
        );
        return (ArrayList<User>) dbUsers.searchUsersByName(name, branchId);
    }

    @RequestMapping(value = "/checkUserEmail/{Email}", method = RequestMethod.GET)
    @ResponseBody
    public boolean checkUserEmail(@PathVariable("Email") String email) {
        return dbUsers.checkExistingEmail(email);
    }

    @RequestMapping(value = "/checkUserUserName/{UserName}", method = RequestMethod.GET)
    @ResponseBody
    public boolean checkUserName(@PathVariable("UserName") String userName) {
        return dbUsers.checkExistUsername(userName);
    }

    @RequestMapping(value = "/getUserImg", method = RequestMethod.GET)
    @ResponseBody
    public String getUserImgByUserName(@RequestParam("id") String id, Principal principal) {
        authorizationService.assertSelfCapability(
                principal.getName(),
                id,
                "profile.self.read"
        );
        return dbUsers.getUserImg(id);
    }

    @Operation(summary = "Get my performance", description = "Retrieves the authenticated user's own sales performance metrics for a given rolling period.")
    @RequestMapping(value = "/performance", method = RequestMethod.GET)
    @ResponseBody
    public UserPerformanceResponse getMyPerformance(
            @RequestParam(value = "period", required = false, defaultValue = "TODAY") String period,
            Principal principal
    ) {
        String userName = extractBaseUserName(principal.getName());

        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                null,
                null,
                "profile.self.performance"
        );

        User user = dbUsers.getUser(userName);
        if (user == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found!");
        }

        int branchId = user.getBranchId();
        if (branchId <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BRANCH_NOT_ASSIGNED", "No active branch assigned to this account");
        }

        Integer companyId = resolveCompanyId(user, userName);

        return userPerformanceService.getPerformance(companyId, branchId, userName, period);
    }

    private Integer resolveCompanyId(User user, String userName) {
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

    @Operation(summary = "Register new user", description = "Public registration endpoint to create a new user account.")
    @PostMapping("/saveNewUser")
    public ResponseEntity<Object> saveNewUser(@Valid @RequestBody SaveUserRequest requestBody) {
        String answer = saveUserInternal(requestBody);
        return ResponseEntity.status(HttpStatus.CREATED).body(answer);
    }

    @PostMapping("/saveUser")
    public ResponseEntity<Object> newUser(@Valid @RequestBody SaveUserRequest requestBody, Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                null,
                requestBody.getBranchId() > 0 ? requestBody.getBranchId() : null,
                "users.account.create"
        );
        String answer = saveUserInternal(requestBody);
        dbUsers.setPasswordResetRequired(requestBody.getUserName(), true);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(answer);
    }

    @PutMapping("/resetPassword/{userName}")
    public ResponseEntity<String> resetPassword(@PathVariable String userName,
                                                Principal principal,
                                                @Valid @RequestBody ResetPasswordRequest requestBody) {
        authorizationService.assertSelfCapability(
                principal.getName(),
                userName,
                "profile.self.edit"
        );
        String answer = dbUsers.updateUserPassword(
                userName,
                requestBody.getOldPassword(),
                requestBody.getPassword()
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(answer);
    }

    @PutMapping("/admin/resetPassword")
    public ResponseEntity<String> adminResetPassword(Principal principal,
                                                     @Valid @RequestBody AdminResetPasswordRequest requestBody) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                null,
                requestBody.getBranchId(),
                "users.account.edit"
        );
        String answer = dbUsers.adminResetUserPassword(
                requestBody.getUserName(),
                requestBody.getBranchId(),
                requestBody.getPassword(),
                requestBody.isPasswordResetRequired()
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(answer);
    }

    @PutMapping("/updateImg/{userName}")
    public ResponseEntity<String> updateImg(@PathVariable String userName,
                                            Principal principal,
                                            @Valid @RequestBody UpdateUserImageRequest requestBody) {
        authorizationService.assertSelfCapability(
                principal.getName(),
                userName,
                "profile.self.edit"
        );
        String answer = dbUsers.updateUserImg(userName, requestBody.getImgFile());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(answer);
    }

    private String saveUserInternal(SaveUserRequest requestBody) {
        return dbUsers.addUser(
                requestBody.getUserName(),
                requestBody.getUserPassword(),
                requestBody.getEmail(),
                requestBody.getUserRole(),
                requestBody.getFirstName(),
                requestBody.getLastName(),
                requestBody.getGender(),
                requestBody.getUserPhone(),
                requestBody.getBranchId(),
                requestBody.getImgFile()
        );
    }

}
