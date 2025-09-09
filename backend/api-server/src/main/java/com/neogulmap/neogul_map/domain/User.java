package com.neogulmap.neogul_map.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import com.neogulmap.neogul_map.dto.UserRequest;
import jakarta.persistence.*;

@Getter
@Setter
@Builder
@Entity
@Table(name = "users")
public class User {
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
    
    @Column(name = "updated_at")
    private String updatedAt;

    public User() {}

    public User(Long id, String nickname, String email, String oauthId, String oauthProvider, String profileImage, String createdAt, String updatedAt) {
        this.id = id;
        this.nickname = nickname;
        this.email = email;
        this.oauthId = oauthId;
        this.oauthProvider = oauthProvider;
        this.profileImage = profileImage;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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
}
