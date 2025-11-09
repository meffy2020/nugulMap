package com.neogulmap.neogul_map.config;

import com.neogulmap.neogul_map.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * StorageService 선택을 위한 설정 클래스
 * Profile에 따라 로컬 또는 S3 저장소를 선택
 */
@Configuration
@RequiredArgsConstructor
public class StorageConfig {
    
    @Value("${app.storage.type:local}")
    private String storageType;
    
    private final StorageService localStorageService;
    
    @Bean
    @Primary
    public StorageService storageService(
            @Qualifier("localStorageService") StorageService localStorageService) {
        
        return switch (storageType.toLowerCase()) {
            case "s3" -> {
                System.out.println("S3 저장소는 현재 비활성화되어 있습니다. 로컬 저장소를 사용합니다.");
                yield localStorageService;
            }
            case "local" -> localStorageService;
            default -> {
                System.out.println("알 수 없는 저장소 타입: " + storageType + ", 로컬 저장소를 사용합니다.");
                yield localStorageService;
            }
        };
    }
}
