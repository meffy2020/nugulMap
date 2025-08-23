package com.neogulmap.neogul_map.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.neogulmap.neogul_map.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    
    // 프로필 이미지가 있는 사용자 조회
    Optional<User> findByProfileImageIsNotNull();
    
    // 특정 OAuth 제공자의 사용자 조회
    Optional<User> findByOauthProviderAndOauthId(String oauthProvider, String oauthId);
}
