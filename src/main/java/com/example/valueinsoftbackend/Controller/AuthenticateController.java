package com.example.valueinsoftbackend.Controller;


import com.example.valueinsoftbackend.Model.AuthenticationRequest;
import com.example.valueinsoftbackend.Model.AuthenticationRespone;
import com.example.valueinsoftbackend.SecurityPack.MyUserDetailsServices;
import com.example.valueinsoftbackend.util.EncriptionByNum;
import com.example.valueinsoftbackend.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

@RestController
@CrossOrigin(origins = "*", exposedHeaders="Access-Control-Allow-Origin")

public class AuthenticateController {
    private AuthenticationRequest auth;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private MyUserDetailsServices myUserDetailsServices;

    @Autowired
    private JwtUtil jwtUtil;



    @RequestMapping(value = "/authenticate",method = RequestMethod.POST)
    public ResponseEntity<?> createAuthenticationToken(@RequestBody AuthenticationRequest authenticationRequest ) throws Exception
    {
        String username= "";


        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(authenticationRequest.getUsername(),authenticationRequest.getPassword()));

        }catch (BadCredentialsException e)
        {
            System.out.println("bad AC");
             throw new Exception("inCorrect user and password");
        }
        System.out.println(authenticationRequest.getUsername());
        final UserDetails userDetails = myUserDetailsServices.loadUserByUsername(authenticationRequest.getUsername());
        final String jwt = jwtUtil.generateToken(userDetails);
        username = jwtUtil.extractUsername(jwt);
        System.out.println("userName:"+jwtUtil.extractUsername(jwt));
        System.out.println("User Role: "+jwtUtil.extractAllClaims(jwt));
        String[] crad = userDetails.getUsername().split(" : ");

        return ResponseEntity.ok(new AuthenticationRespone(jwt, crad[0], crad[1]).getData()); // crad-> 0 for name and 1-> for role
    }


}
