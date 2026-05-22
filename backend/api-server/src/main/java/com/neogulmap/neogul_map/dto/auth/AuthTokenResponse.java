package com.neogulmap.neogul_map.dto.auth;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthTokenResponse {
    private String accessToken;
    private String refreshToken;
    private boolean profileComplete;
    private UserSummary user;

    @Getter
    @Builder
    public static class UserSummary {
        private Long id;
        private String email;
        private String nickname;
    }
}
