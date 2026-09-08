package com.neogulmap.neogul_map.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.neogulmap.neogul_map.domain.User;
import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.id = :id")
    Optional<User> findForUpdateById(@Param("id") Long id);
    
    // 프로필 이미지가 있는 사용자 조회
    Optional<User> findByProfileImageIsNotNull();
    
    // 특정 OAuth 제공자의 사용자 조회
    Optional<User> findByOauthProviderAndOauthId(String oauthProvider, String oauthId);
}
