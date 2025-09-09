package com.neogulmap.neogul_map.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import com.neogulmap.neogul_map.domain.User;

@Getter
@Setter
@Builder
public class UserResponse {
    private Long id;
    private String email;
    private String nickname;
    private String profileImage;
    private String createdAt;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .profileImage(user.getProfileImage() != null ? "/images/" + user.getProfileImage() : null)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
