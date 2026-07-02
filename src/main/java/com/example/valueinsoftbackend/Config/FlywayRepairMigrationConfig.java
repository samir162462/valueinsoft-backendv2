package com.example.valueinsoftbackend.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayRepairMigrationConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayRepairMigrationConfig.class);

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy(
            @Value("${vls.flyway.repair-on-migrate:false}") boolean repairOnMigrate
    ) {
        return flyway -> {
            if (repairOnMigrate) {
                log.warn("Running Flyway repair before migrate because vls.flyway.repair-on-migrate=true");
                flyway.repair();
            }
            flyway.migrate();
        };
    }
}
