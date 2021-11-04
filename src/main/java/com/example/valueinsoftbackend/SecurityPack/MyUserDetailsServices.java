package com.example.valueinsoftbackend.SecurityPack;

import com.example.valueinsoftbackend.DatabaseRequests.DbUsers;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;


@Service
public class MyUserDetailsServices implements UserDetailsService {




    @Override
    public UserDetails loadUserByUsername(String userName) throws UsernameNotFoundException {

        com.example.valueinsoftbackend.Model.User user = DbUsers.getUser(userName);
        System.out.println(user.getUserName());
        return new User(user.getUserName(),user.getUserPassword(),new ArrayList<>());

    }
}
