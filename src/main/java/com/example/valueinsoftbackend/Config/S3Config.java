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
            builder.endpointOverride(URI.create(s3Properties.getEndpoint()));
        }
        
        // Comprehensive compatibility for S3-compatible systems
        builder.serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                .checksumValidationEnabled(false)
                .chunkedEncodingEnabled(false)
                .pathStyleAccessEnabled(false) // Reverting back to Virtual Host, which is correct for Tigris
                .build());

        builder.overrideConfiguration(c -> c.addExecutionInterceptor(new software.amazon.awssdk.core.interceptor.ExecutionInterceptor() {
            @Override
            public software.amazon.awssdk.http.SdkHttpRequest modifyHttpRequest(software.amazon.awssdk.core.interceptor.Context.ModifyHttpRequest context, software.amazon.awssdk.core.interceptor.ExecutionAttributes executionAttributes) {
                if (context.httpRequest().headers().containsKey("Expect")) {
                    return context.httpRequest().toBuilder().removeHeader("Expect").build();
                }
                return context.httpRequest();
            }
        }));

        builder.httpClientBuilder(software.amazon.awssdk.http.apache.ApacheHttpClient.builder()
                .expectContinueEnabled(false));

        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        software.amazon.awssdk.services.s3.presigner.S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(s3Properties.getRegion()))
                .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                        .pathStyleAccessEnabled(false)
                        .build());

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
