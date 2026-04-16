package com.example.valueinsoftbackend.Filters;

import lombok.extern.slf4j.Slf4j;

import com.example.valueinsoftbackend.SecurityPack.MyUserDetailsServices;
import com.example.valueinsoftbackend.util.JwtUtil;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
@Slf4j
public class JwtRequestFilter extends OncePerRequestFilter {

    @Autowired
    private MyUserDetailsServices myUserDetailsServices;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        final String authorizationHeader = request.getHeader("Authorization");
        String username = null;
        String jwt = null;

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7);
            try {
                username = jwtUtil.extractUsername(jwt);
            } catch (JwtException | IllegalArgumentException exception) {
                log.warn("JWT extraction failed for request {} {}: {}", request.getMethod(), request.getRequestURI(), exception.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        log.info("Filter Detail | Request: {} {} | Extracted Username: {} | Current Context: {}", 
                request.getMethod(), request.getRequestURI(), 
                username != null ? username : "NONE",
                SecurityContextHolder.getContext().getAuthentication() != null ? "AUTHENTICATED" : "EMPTY");

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            log.info("Processing authentication for user: {}", username);
            try {
                UserDetails userDetails = myUserDetailsServices.loadUserByUsername(username);
                if (jwtUtil.validateToken(jwt, userDetails)) {
                    log.info("JWT validated successfully for user: {}", username);
                    UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    usernamePasswordAuthenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
                } else {
                    log.warn("JWT validation failed for user: {}", username);
                }
            } catch (JwtException | IllegalArgumentException | UsernameNotFoundException exception) {
                log.warn("Authentication context population failed: {}", exception.getMessage());
                SecurityContextHolder.clearContext();
            }
        } else if (username == null && SecurityContextHolder.getContext().getAuthentication() == null) {
            log.info("No valid authentication found for request: {} {}", request.getMethod(), request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }
}
