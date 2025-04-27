package com.example.valueinsoftbackend.Controller;


import com.example.valueinsoftbackend.DatabaseRequests.DbUsers;
import com.example.valueinsoftbackend.Model.AuthenticationRequest;
import com.example.valueinsoftbackend.Model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
@CrossOrigin("*")

public class UserController {



    private final DbUsers dbUsers;

    @Autowired
    public UserController(DbUsers dbUsers) {
        this.dbUsers = dbUsers;
    }

    @RequestMapping(value = "/getUser", method = RequestMethod.GET)
    @ResponseBody
    public User getPersonsByNames(@RequestParam("id") String id) {
        return dbUsers.getUser(id);
    }

    @RequestMapping(value = "/getUserDetails/{userName}", method = RequestMethod.GET)
    @ResponseBody
    public User getUserByName(@PathVariable("userName") String userName ) {
        return dbUsers.getUserDetails(userName);
    }

    @RequestMapping(value = "/{companyId}/{branchId}/getAllUsers", method = RequestMethod.GET)
    @ResponseBody
    public ArrayList<User> getAllUsers(@PathVariable("branchId") int branchId ,@PathVariable("companyId") int companyId ) {
        return (ArrayList<User>) dbUsers.getAllUsers(branchId);
    }
    @RequestMapping(value = "/checkUserEmail/{Email}", method = RequestMethod.GET)
    @ResponseBody
    public boolean checkUserEmail(@PathVariable("Email") String Email) {
        return dbUsers.checkExistingEmail(Email);
    }
    @RequestMapping(value = "/checkUserUserName/{UserName}", method = RequestMethod.GET)
    @ResponseBody
    public boolean checkUserName(@PathVariable("UserName") String UserName) {
        return dbUsers.checkExistUsername(UserName);
    }


    @RequestMapping(value = "/getUserImg", method = RequestMethod.GET)
    @ResponseBody
    public String getUserImgByUserName(@RequestParam("id") String id) {
        return dbUsers.getUserImg(id);
    }


    @PostMapping("/saveNewUser")

    public ResponseEntity<Object> saveNewUser(@RequestBody Map<String, String> requestBody) {

        String answer ="Cant Add New User";
        try {
            String inputString = requestBody.get("imgFile");
             answer = dbUsers.addUser(requestBody.get("userName"), requestBody.get("userPassword"), requestBody.get("email"), requestBody.get("userRole"), requestBody.get("firstName")
                    , requestBody.get("lastName"), Integer.valueOf(requestBody.get("gender")), requestBody.get("userPhone"), Integer.valueOf(requestBody.get("branchId")), inputString);
            if (answer.contains( "user exist")) {//email exist
                return ResponseEntity.status(HttpStatus.ALREADY_REPORTED).body(answer);

            }
            if (answer.contains( "email exist")) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(answer);

            }
        }catch(Exception e)
        {
            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body(answer);

        }



        return ResponseEntity.status(HttpStatus.CREATED).body(answer);

    }

    @PostMapping("/saveUser")
    public ResponseEntity<Object> newUser(@RequestBody Map<String, String> requestBody) {
        String inputString = requestBody.get("imgFile");
        String answer = dbUsers.addUser(requestBody.get("userName"), requestBody.get("userPassword"), requestBody.get("email"), requestBody.get("role"), requestBody.get("firstName")
                , requestBody.get("lastName"), Integer.valueOf(requestBody.get("gender")), requestBody.get("userPhone")
                , Integer.valueOf(requestBody.get("branchId"))
                , inputString);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(answer);
    }

    @PutMapping("/resetPassword/{userName}")
    public ResponseEntity<String> resetPassword(@PathVariable String userName, @RequestBody Map<String, String> requestBody) {

        return dbUsers.updateUserPassword(userName,requestBody.get("oldPassword"),requestBody.get("password"));
    }
    @PutMapping("/updateImg/{userName}")
    public ResponseEntity<String> updateImg(@PathVariable String userName, @RequestBody Map<String, String> requestBody) {

        return dbUsers.updateUserImg(userName,requestBody.get("imgFile"));
    }
}

