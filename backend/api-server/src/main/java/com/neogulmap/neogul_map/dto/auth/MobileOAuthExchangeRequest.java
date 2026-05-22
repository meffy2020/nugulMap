package com.neogulmap.neogul_map.dto.auth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MobileOAuthExchangeRequest {
    private String code;
    private String codeVerifier;
}
