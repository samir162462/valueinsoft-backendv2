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



    @GetMapping
    public String Adduser()
    {
        return "hello fuckers" ;
    }





    @RequestMapping(value = "/getUser", method = RequestMethod.GET)
    @ResponseBody
    public User getPersonsByNames(


            @RequestParam("id") String id

    ) {


        return  DbUsers.getUser(id);
    }


    @PostMapping("/saveUser")

    public ResponseEntity<Object> newUser(@RequestBody Map<String, String> requestBody) {


        String answer = DbUsers.AddUser(requestBody.get("userName"),requestBody.get("userPassword"),requestBody.get("email"),requestBody.get("role"),requestBody.get("firstName")
                ,requestBody.get("lastName"),Integer.valueOf(requestBody.get("gender")),requestBody.get("userPhone"),Integer.valueOf(requestBody.get("branchId")) );



        return ResponseEntity.status(HttpStatus.ACCEPTED).body(answer);

    }

}

