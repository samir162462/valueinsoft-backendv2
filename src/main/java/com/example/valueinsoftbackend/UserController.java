package com.example.valueinsoftbackend;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;

@RestController
@RequestMapping("/users")

public class UserController {

    ArrayList<User> UserValues()
    {
        ArrayList<User> userList = new ArrayList();
        User u1 = new User(10,"samir","1234");
        User u2 = new User(11,"mo","0000");
        User u3 = new User(12,"khaled","1111");
        userList.add(u1);
        userList.add(u2);
        userList.add(u3);
        return  userList;

    }

    @GetMapping
    public ArrayList<User> getgreet()
    {
        return UserValues();
    }

}
