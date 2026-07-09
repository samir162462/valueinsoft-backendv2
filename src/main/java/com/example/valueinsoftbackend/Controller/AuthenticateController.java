package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.AuthenticationRequest;
import com.example.valueinsoftbackend.Model.AuthenticationResponse;
import com.example.valueinsoftbackend.SecurityPack.LoginAttemptService;
import com.example.valueinsoftbackend.SecurityPack.MyUserDetailsServices;
import com.example.valueinsoftbackend.util.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
public class AuthenticateController {

    private final AuthenticationManager authenticationManager;
    private final MyUserDetailsServices myUserDetailsServices;
    private final JwtUtil jwtUtil;
    private final LoginAttemptService loginAttemptService;

    public AuthenticateController(AuthenticationManager authenticationManager,
                                  MyUserDetailsServices myUserDetailsServices,
                                  JwtUtil jwtUtil,
                                  LoginAttemptService loginAttemptService) {
        this.authenticationManager = authenticationManager;
        this.myUserDetailsServices = myUserDetailsServices;
        this.jwtUtil = jwtUtil;
        this.loginAttemptService = loginAttemptService;
    }

    @RequestMapping(value = "/authenticate", method = RequestMethod.POST)
    public ResponseEntity<AuthenticationResponse> createAuthenticationToken(@Valid @RequestBody AuthenticationRequest authenticationRequest,
                                                                            HttpServletRequest httpRequest) {
        String clientIp = resolveClientIp(httpRequest);

        // P1-5: block brute-force / credential-stuffing before attempting authentication.
        loginAttemptService.assertNotLocked(authenticationRequest.username(), clientIp);

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            authenticationRequest.username(),
                            authenticationRequest.password()
                    )
            );
        } catch (AuthenticationException exception) {
            loginAttemptService.recordFailure(authenticationRequest.username(), clientIp);
            throw exception;
        }

        loginAttemptService.reset(authenticationRequest.username(), clientIp);

        final UserDetails userDetails = myUserDetailsServices.loadUserByUsername(authenticationRequest.username());
        final String jwt = jwtUtil.generateToken(userDetails);
        String[] card = userDetails.getUsername().split(" : ");
        boolean passwordResetRequired = myUserDetailsServices.isPasswordResetRequired(card[0]);
        return ResponseEntity.ok(new AuthenticationResponse(jwt, card[0], card[1], passwordResetRequired));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
