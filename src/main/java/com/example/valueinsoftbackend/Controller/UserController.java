package com.example.valueinsoftbackend.Controller;


import com.example.valueinsoftbackend.DatabaseRequests.DbUsers;
import com.example.valueinsoftbackend.Model.AuthenticationRequest;
import com.example.valueinsoftbackend.Model.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
@CrossOrigin("*")

public class UserController {


    @RequestMapping(value = "/getUser", method = RequestMethod.GET)
    @ResponseBody
    public User getPersonsByNames(@RequestParam("id") String id) {
        return DbUsers.getUser(id);
    }
    @RequestMapping(value = "/checkUserEmail/{Email}", method = RequestMethod.GET)
    @ResponseBody
    public boolean checkUserEmail(@PathVariable("Email") String Email) {
        return DbUsers.checkExistingEmail(Email);
    }
    @RequestMapping(value = "/checkUserUserName/{UserName}", method = RequestMethod.GET)
    @ResponseBody
    public boolean checkUserName(@PathVariable("UserName") String UserName) {
        return DbUsers.checkExistUsername(UserName);
    }


    @RequestMapping(value = "/getUserImg", method = RequestMethod.GET)
    @ResponseBody
    public String getUserImgByUserName(@RequestParam("id") String id) {
        return DbUsers.getUserImg(id);
    }


    @PostMapping("/saveNewUser")

    public ResponseEntity<Object> saveNewUser(@RequestBody Map<String, String> requestBody) {

        String inputString = requestBody.get("imgFile");
        String answer = DbUsers.AddUser(requestBody.get("userName"), requestBody.get("userPassword"), requestBody.get("email"), requestBody.get("userRole"), requestBody.get("firstName")
                , requestBody.get("lastName"), Integer.valueOf(requestBody.get("gender")), requestBody.get("userPhone"), Integer.valueOf(requestBody.get("branchId")), inputString);


        return ResponseEntity.status(HttpStatus.ACCEPTED).body(answer);

    }

    @PostMapping("/saveUser")

    public ResponseEntity<Object> newUser(@RequestBody Map<String, String> requestBody) {

        String inputString = requestBody.get("imgFile");

        String answer = DbUsers.AddUser(requestBody.get("userName"), requestBody.get("userPassword"), requestBody.get("email"), requestBody.get("role"), requestBody.get("firstName")
                , requestBody.get("lastName"), Integer.valueOf(requestBody.get("gender")), requestBody.get("userPhone")
                , Integer.valueOf(requestBody.get("branchId"))
                , inputString);


        return ResponseEntity.status(HttpStatus.ACCEPTED).body(answer);

    }
}

