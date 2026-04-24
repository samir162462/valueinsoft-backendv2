package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.DatabaseRequests.DbUsers;
import com.example.valueinsoftbackend.Service.AuthorizationService;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Request.ResetPasswordRequest;
import com.example.valueinsoftbackend.Model.Request.SaveUserRequest;
import com.example.valueinsoftbackend.Model.Request.UpdateUserImageRequest;
import com.example.valueinsoftbackend.Model.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.ArrayList;

@RestController
@RequestMapping("/users")
public class UserController {

    private final DbUsers dbUsers;
    private final AuthorizationService authorizationService;

    public UserController(DbUsers dbUsers, AuthorizationService authorizationService) {
        this.dbUsers = dbUsers;
        this.authorizationService = authorizationService;
    }

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
