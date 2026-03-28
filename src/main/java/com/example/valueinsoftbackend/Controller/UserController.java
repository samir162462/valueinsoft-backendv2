package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.DatabaseRequests.DbUsers;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Map;

@RestController
@RequestMapping("/users")
public class UserController {

    private final DbUsers dbUsers;

    public UserController(DbUsers dbUsers) {
        this.dbUsers = dbUsers;
    }

    @RequestMapping(value = "/getUser", method = RequestMethod.GET)
    @ResponseBody
    public User getPersonsByNames(@RequestParam("id") String id) {
        User user = dbUsers.getUser(id);
        if (user == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found!");
        }
        return user;
    }

    @RequestMapping(value = "/getUserDetails/{userName}", method = RequestMethod.GET)
    @ResponseBody
    public User getUserByName(@PathVariable("userName") String userName) {
        User user = dbUsers.getUserDetails(userName);
        if (user == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found!");
        }
        return user;
    }

    @RequestMapping(value = "/{companyId}/{branchId}/getAllUsers", method = RequestMethod.GET)
    @ResponseBody
    public ArrayList<User> getAllUsers(@PathVariable("branchId") int branchId, @PathVariable("companyId") int companyId) {
        return (ArrayList<User>) dbUsers.getAllUsers(branchId);
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
    public String getUserImgByUserName(@RequestParam("id") String id) {
        return dbUsers.getUserImg(id);
    }

    @PostMapping("/saveNewUser")
    public ResponseEntity<Object> saveNewUser(@RequestBody Map<String, String> requestBody) {
        String answer = dbUsers.addUser(
                requireField(requestBody, "userName"),
                requireField(requestBody, "userPassword"),
                requireField(requestBody, "email"),
                requireField(requestBody, "userRole"),
                requireField(requestBody, "firstName"),
                requireField(requestBody, "lastName"),
                Integer.parseInt(requireField(requestBody, "gender")),
                requireField(requestBody, "userPhone"),
                Integer.parseInt(requireField(requestBody, "branchId")),
                requestBody.get("imgFile")
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(answer);
    }

    @PostMapping("/saveUser")
    public ResponseEntity<Object> newUser(@RequestBody Map<String, String> requestBody) {
        String answer = dbUsers.addUser(
                requireField(requestBody, "userName"),
                requireField(requestBody, "userPassword"),
                requireField(requestBody, "email"),
                requireField(requestBody, "role"),
                requireField(requestBody, "firstName"),
                requireField(requestBody, "lastName"),
                Integer.parseInt(requireField(requestBody, "gender")),
                requireField(requestBody, "userPhone"),
                Integer.parseInt(requireField(requestBody, "branchId")),
                requestBody.get("imgFile")
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(answer);
    }

    @PutMapping("/resetPassword/{userName}")
    public ResponseEntity<String> resetPassword(@PathVariable String userName, @RequestBody Map<String, String> requestBody) {
        String answer = dbUsers.updateUserPassword(
                userName,
                requireField(requestBody, "oldPassword"),
                requireField(requestBody, "password")
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(answer);
    }

    @PutMapping("/updateImg/{userName}")
    public ResponseEntity<String> updateImg(@PathVariable String userName, @RequestBody Map<String, String> requestBody) {
        String answer = dbUsers.updateUserImg(userName, requireField(requestBody, "imgFile"));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(answer);
    }

    private String requireField(Map<String, String> requestBody, String fieldName) {
        String value = requestBody.get(fieldName);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
