package com.neogulmap.neogul_map.dto;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

@Getter
@Setter

public class UserRequest {
    @NotBlank(message = "이메일은 필수입니다")
    @Email(message = "올바른 이메일 형식이 아닙니다")
    private String email;
    
    private String oauthId;
    
    @Pattern(regexp = "^(kakao|google|naver)$", message = "지원하지 않는 OAuth 제공자입니다")
    private String oauthProvider;
    
    @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하여야 합니다")
    private String nickname;
    
    private String profileImage;
    
    private String createdAt;
}
