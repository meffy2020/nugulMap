package com.neogulmap.neogul_map.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;

/**
 * AWS S3 / MinIO 설정 클래스
 * endpoint가 설정되어 있으면 MinIO, 없으면 AWS S3 사용
 */
@Configuration
@ConditionalOnProperty(name = "cloud.aws.s3.endpoint", matchIfMissing = false)
public class S3Config {
    
    @Value("${cloud.aws.credentials.access-key}")
    private String accessKey;
    
    @Value("${cloud.aws.credentials.secret-key}")
    private String secretKey;
    
    @Value("${cloud.aws.region.static:us-east-1}")
    private String region;
    
    @Value("${cloud.aws.s3.endpoint:#{null}}")
    private String endpoint;
    
    @Bean
    public S3Client s3Client() {
        AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(accessKey, secretKey);
        
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(awsCredentials));
        
        // MinIO 사용 시 endpoint 설정 및 path-style 활성화
        if (endpoint != null && !endpoint.isEmpty()) {
            builder.endpointOverride(URI.create(endpoint))
                   .forcePathStyle(true); // MinIO 사용 시 필수
        }
        
        return builder.build();
    }
}
