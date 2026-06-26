package com.neogulmap.neogul_map.dto.auth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AppleMobileLoginRequest {
    private String identityToken;
    private String authorizationCode;
    private String fullName;
}
