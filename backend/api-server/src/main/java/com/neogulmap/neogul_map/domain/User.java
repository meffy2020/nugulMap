package com.neogulmap.neogul_map.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import com.neogulmap.neogul_map.dto.UserRequest;
import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

@Getter
@Setter
@Builder
@Entity
@Table(name = "users")
public class User implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "nickname")
    private String nickname;
    
    @Column(name = "email", unique = true, nullable = false)
    private String email;
    
    @Column(name = "oauth_id", unique = true, nullable = false)
    private String oauthId;
    
    @Column(name = "oauth_provider", nullable = false)
    private String oauthProvider;
    
    @Column(name = "profile_image_url")
    private String profileImage;
    
    @Column(name = "created_at")
    private String createdAt;

    public User() {}

    public User(Long id, String nickname, String email, String oauthId, String oauthProvider, String profileImage, String createdAt) {
        this.id = id;
        this.nickname = nickname;
        this.email = email;
        this.oauthId = oauthId;
        this.oauthProvider = oauthProvider;
        this.profileImage = profileImage;
        this.createdAt = createdAt;
    }

    public void update(UserRequest userRequest) {
        if (userRequest.getNickname() != null) {
            this.nickname = userRequest.getNickname();
        }
        if (userRequest.getProfileImage() != null) {
            // 프로필 이미지 경로를 profiles/ 디렉토리로 설정
            if (!userRequest.getProfileImage().startsWith("profiles/")) {
                this.profileImage = "profiles/" + userRequest.getProfileImage();
            } else {
                this.profileImage = userRequest.getProfileImage();
            }
        }
    }

    // UserDetails 인터페이스 구현
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return null; // OAuth 사용자는 패스워드가 없음
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
