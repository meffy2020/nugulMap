package com.neogulmap.neogul_map.config;

import com.neogulmap.neogul_map.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * StorageService 선택을 위한 설정 클래스
 * Profile에 따라 로컬 또는 S3 저장소를 선택
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class StorageConfig {
    
    @Value("${app.storage.type:local}")
    private String storageType;
    
    private final StorageService localStorageService;
    
    @Bean
    @Primary
    public StorageService storageService(
            @Qualifier("localStorageService") StorageService localStorageService,
            @Qualifier("s3StorageService") StorageService s3StorageService) {
        
        return switch (storageType.toLowerCase()) {
            case "s3" -> {
                log.info("S3 저장소를 사용합니다.");
                yield s3StorageService;
            }
            case "local" -> {
                log.info("로컬 저장소를 사용합니다.");
                yield localStorageService;
            }
            default -> {
                log.warn("알 수 없는 저장소 타입: {}, 로컬 저장소를 사용합니다.", storageType);
                yield localStorageService;
            }
        };
    }
}
