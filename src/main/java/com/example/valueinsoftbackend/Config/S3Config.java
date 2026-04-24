package com.example.valueinsoftbackend.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class S3Config {

    private final S3Properties s3Properties;

    public S3Config(S3Properties s3Properties) {
        this.s3Properties = s3Properties;
    }

    @Bean
    public S3Client s3Client() {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(s3Properties.getRegion()));

        if (isCredentialsProvided()) {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(
                    s3Properties.getAccessKey(),
                    s3Properties.getSecretKey()
            );
            builder.credentialsProvider(StaticCredentialsProvider.create(credentials));
        }

        if (s3Properties.getEndpoint() != null && !s3Properties.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(s3Properties.getEndpoint()))
                   .forcePathStyle(true);
        }

        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        software.amazon.awssdk.services.s3.presigner.S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(s3Properties.getRegion()));

        if (isCredentialsProvided()) {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(
                    s3Properties.getAccessKey(),
                    s3Properties.getSecretKey()
            );
            builder.credentialsProvider(StaticCredentialsProvider.create(credentials));
        }

        if (s3Properties.getEndpoint() != null && !s3Properties.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(s3Properties.getEndpoint()));
        }

        return builder.build();
    }

    private boolean isCredentialsProvided() {
        return s3Properties.getAccessKey() != null && !s3Properties.getAccessKey().isBlank() &&
               s3Properties.getSecretKey() != null && !s3Properties.getSecretKey().isBlank();
    }
}
