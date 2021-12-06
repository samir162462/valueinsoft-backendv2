package com.example.valueinsoftbackend.SecurityPack;


import com.example.valueinsoftbackend.Filters.JwtRequestFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;

@EnableWebSecurity
@Configuration

public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
    @Autowired
    private MyUserDetailsServices myUserDetailsServices;


    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception{

        auth.userDetailsService(myUserDetailsServices);
    }

    @Autowired
    protected JwtRequestFilter JwtRequestFilter;

    @Override
    protected void configure(HttpSecurity http) throws Exception{

        http.cors().and().csrf().disable().authorizeRequests().antMatchers().permitAll().antMatchers("/authenticate").permitAll().antMatchers("/users/saveUser").permitAll().antMatchers("/Company/getCompany").permitAll()
                .antMatchers("/Company/getCompanyById").permitAll()
                .antMatchers("/users/saveUser").hasAnyAuthority("Admin","Owner").
                anyRequest().authenticated().and().sessionManagement().
                sessionCreationPolicy(SessionCreationPolicy.STATELESS).and();
        http.addFilterBefore(JwtRequestFilter, UsernamePasswordAuthenticationFilter.class);
    }



    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        final CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(new ArrayList<>(
                Arrays.asList("http://localhost:3000")));
        configuration.setAllowedMethods(new ArrayList<>(
                Arrays.asList("HEAD",
                "GET", "POST", "PUT", "DELETE", "PATCH")));
        // setAllowCredentials(true) is important, otherwise:
        // The value of the 'Access-Control-Allow-Origin' header in the response must not be the wildcard '*' when the request's credentials mode is 'include'.
        configuration.setAllowCredentials(true);
        // setAllowedHeaders is important! Without it, OPTIONS preflight request
        // will fail with 403 Invalid CORS request
        configuration.setAllowedHeaders(new ArrayList<>(
                Arrays.asList("Authorization", "Cache-Control", "Content-Type")));
        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManagerBean() throws Exception{
        return super.authenticationManagerBean();
    }


    @Bean
    PasswordEncoder passwordEncoder()
    {
        return NoOpPasswordEncoder.getInstance();
    }

}
