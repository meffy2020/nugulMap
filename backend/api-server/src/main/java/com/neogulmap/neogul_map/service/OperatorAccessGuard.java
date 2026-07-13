package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.BusinessBaseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class OperatorAccessGuard {

    private final String configuredKey;

    public OperatorAccessGuard(@Value("${app.moderation.operator-key:}") String configuredKey) {
        this.configuredKey = configuredKey;
    }

    public void requireAccess(String providedKey) {
        if (configuredKey == null || configuredKey.isBlank()) {
            throw new BusinessBaseException(
                    ErrorCode.OPERATOR_ACCESS_NOT_CONFIGURED,
                    "운영자 조회 키가 구성되지 않았습니다."
            );
        }
        byte[] configured = configuredKey.getBytes(StandardCharsets.UTF_8);
        byte[] provided = providedKey == null
                ? new byte[0]
                : providedKey.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(configured, provided)) {
            throw new BusinessBaseException(ErrorCode.OPERATOR_ACCESS_DENIED, "운영자 접근 권한이 없습니다.");
        }
    }
}
