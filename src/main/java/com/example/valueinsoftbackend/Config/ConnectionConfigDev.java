/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@Configuration
@Profile("dev")
@Slf4j
public class ConnectionConfigDev {
    final private String url = "jdbc:postgresql://localhost:5432/localvls";
    @Bean
    public DataSource PostgresDataSource() {
        log.info("Inside PostgresDataSource Dev");
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername("postgres");
        dataSource.setPassword("0000");

        return dataSource;
    }

    @Bean
    JdbcTemplate jdbcTemplate(){
        return new JdbcTemplate(PostgresDataSource());
    }

}

