package com.example.valueinsoftbackend.SecurityPack;

import com.example.valueinsoftbackend.DatabaseRequests.DbUsers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.*;


@Service
public class MyUserDetailsServices implements UserDetailsService {


    private final DbUsers dbUsers;

    @Autowired
    public MyUserDetailsServices(DbUsers dbUsers) {
        this.dbUsers = dbUsers;
    }

    public Collection<? extends GrantedAuthority> getAuthorities(com.example.valueinsoftbackend.Model.User user) {
        Set<String> roles = new HashSet<>() ;
         roles.add(user.getRole());
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();

        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority(role));
        }

        return authorities;
    }


    @Override
    public UserDetails loadUserByUsername(String userName) throws UsernameNotFoundException {

        System.out.println("loadUserByUsername: "+userName);
        com.example.valueinsoftbackend.Model.User user = null;
        if (userName.contains(" : ")) {
            user = dbUsers.getUser(userName.split(" : ")[0]);
        }else
        {
            user = dbUsers.getUser(userName);
        }
        // System.out.println();
        //System.out.println(user.getUserName());
        ArrayList<String> claims = new ArrayList<>();
        claims.add(""+user.getRole());
        System.out.println(""+user.getRole());
        return new User(user.getUserName()+ " : "+user.getRole(),user.getUserPassword(),getAuthorities(user));
    }
}
