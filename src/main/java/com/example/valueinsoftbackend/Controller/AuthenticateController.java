package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.AuthenticationRequest;
import com.example.valueinsoftbackend.Model.AuthenticationResponse;
import com.example.valueinsoftbackend.SecurityPack.MyUserDetailsServices;
import com.example.valueinsoftbackend.util.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
public class AuthenticateController {

    private final AuthenticationManager authenticationManager;
    private final MyUserDetailsServices myUserDetailsServices;
    private final JwtUtil jwtUtil;

    public AuthenticateController(AuthenticationManager authenticationManager,
                                  MyUserDetailsServices myUserDetailsServices,
                                  JwtUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.myUserDetailsServices = myUserDetailsServices;
        this.jwtUtil = jwtUtil;
    }

    @RequestMapping(value = "/authenticate", method = RequestMethod.POST)
    public ResponseEntity<AuthenticationResponse> createAuthenticationToken(@Valid @RequestBody AuthenticationRequest authenticationRequest) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        authenticationRequest.username(),
                        authenticationRequest.password()
                )
        );

        final UserDetails userDetails = myUserDetailsServices.loadUserByUsername(authenticationRequest.username());
        final String jwt = jwtUtil.generateToken(userDetails);
        String[] card = userDetails.getUsername().split(" : ");
        return ResponseEntity.ok(new AuthenticationResponse(jwt, card[0], card[1]));
    }
}
