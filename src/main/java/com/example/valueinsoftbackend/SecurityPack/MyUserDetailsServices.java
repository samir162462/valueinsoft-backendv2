package com.example.valueinsoftbackend.SecurityPack;

import com.example.valueinsoftbackend.DatabaseRequests.DbUsers;
import com.example.valueinsoftbackend.Model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class MyUserDetailsServices implements UserDetailsService, UserDetailsPasswordService {

    private static final Logger log = LoggerFactory.getLogger(MyUserDetailsServices.class);

    private final DbUsers dbUsers;

    public MyUserDetailsServices(DbUsers dbUsers) {
        this.dbUsers = dbUsers;
    }

    public Collection<? extends GrantedAuthority> getAuthorities(User user) {
        Set<String> roles = new HashSet<>();
        roles.add(user.getRole());
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();

        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority(role));
        }

        return authorities;
    }

    @Override
    public UserDetails loadUserByUsername(String userName) throws UsernameNotFoundException {
        String resolvedUserName = extractBaseUserName(userName);
        User user = dbUsers.getUser(resolvedUserName);

        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + resolvedUserName);
        }

        log.debug("Loaded user {}", resolvedUserName);
        return new org.springframework.security.core.userdetails.User(
                user.getUserName() + " : " + user.getRole(),
                user.getUserPassword(),
                getAuthorities(user)
        );
    }

    @Override
    public UserDetails updatePassword(UserDetails user, String newPassword) {
        String resolvedUserName = extractBaseUserName(user.getUsername());
        dbUsers.upgradeStoredPassword(resolvedUserName, newPassword);
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                newPassword,
                user.getAuthorities()
        );
    }

    private String extractBaseUserName(String value) {
        if (value != null && value.contains(" : ")) {
            return value.split(" : ")[0];
        }
        return value;
    }
}
