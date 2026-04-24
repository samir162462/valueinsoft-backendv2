package com.example.valueinsoftbackend.Config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
@ConfigurationProperties(prefix = "vls.s3")
@Getter
@Setter
@Slf4j
public class S3Properties {

    private String endpoint;
    private String region = "us-east-1";
    private String accessKey;
    private String secretKey;
    private String bucketName;

    @PostConstruct
    void initialize() {
        log.info("S3 storage configured with endpoint: {}, region: {}, bucket: {}", 
            endpoint, region, bucketName);
    }
}
