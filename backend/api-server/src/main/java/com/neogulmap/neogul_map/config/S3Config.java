package com.neogulmap.neogul_map.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * AWS S3 설정 클래스
 * S3 설정이 있을 때만 활성화
 */
@Configuration
@ConditionalOnProperty(name = "app.s3.access-key", havingValue = "true", matchIfMissing = false)
public class S3Config {
    
    @Value("${app.s3.access-key}")
    private String accessKey;
    
    @Value("${app.s3.secret-key}")
    private String secretKey;
    
    @Value("${app.s3.region:ap-northeast-2}")
    private String region;
    
    @Bean
    public S3Client s3Client() {
        AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(accessKey, secretKey);
        
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
                .build();
    }
}
