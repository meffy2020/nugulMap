package com.neogulmap.neogul_map.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRequest {
    @NotBlank(message = "이메일은 필수입니다")
    @Email(message = "올바른 이메일 형식이 아닙니다")
    private String email;
    
    @NotBlank(message = "OAuth ID는 필수입니다")
    @Size(max = 255, message = "OAuth ID는 255자를 초과할 수 없습니다")
    private String oauthId;
    
    @NotBlank(message = "OAuth 제공자는 필수입니다")
    @Pattern(regexp = "^(kakao|google|naver)$", message = "지원하지 않는 OAuth 제공자입니다")
    private String oauthProvider;
    
    @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하여야 합니다")
    private String nickname;
    
    // 프로필 이미지 경로 (파일명만 저장)
    @Size(max = 255, message = "프로필 이미지 경로는 255자를 초과할 수 없습니다")
    private String profileImage;
    
    // 생성 시간은 자동 설정되므로 검증 불필요
    private String createdAt;
}
