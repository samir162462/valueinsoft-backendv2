/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Config;

import com.example.valueinsoftbackend.SecurityPack.LegacyAwareBcryptPasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement
public class MainConfig {

    @Bean
    public DataSource postgresDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password:}") String password,
            @Value("${spring.datasource.driver-class-name:org.postgresql.Driver}") String driverClassName) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(driverClassName);
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource postgresDataSource) {
        return new JdbcTemplate(postgresDataSource);
    }

    @Bean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource postgresDataSource) {
        return new NamedParameterJdbcTemplate(postgresDataSource);
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource postgresDataSource) {
        return new DataSourceTransactionManager(postgresDataSource);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new LegacyAwareBcryptPasswordEncoder();
    }
}
