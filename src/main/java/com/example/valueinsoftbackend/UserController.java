package com.example.valueinsoftbackend;


import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/users")

public class UserController {
    ArrayList<User> userList = new ArrayList();

    ArrayList<User> UserValues()
    {
        User u1 = new User("10","samir","1234");
        User u2 = new User("11","mo","0000");
        User u3 = new User("12","khaled","1111");
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





    @RequestMapping(value = "/PidName", method = RequestMethod.GET)
    @ResponseBody
    public User getPersonsByNames(


            @RequestParam("id") List<String> id

    ) {
        UserValues();
        System.out.println(id.get(1));

        for (int i = 0; i < userList.size(); i++) {
            if (userList.get(i).getUserName().contains(id.get(0)) && userList.get(i).getUserPassword().contains(id.get(1))) {
            return userList.get(i);
            }
        }
        return null;
    }

}

