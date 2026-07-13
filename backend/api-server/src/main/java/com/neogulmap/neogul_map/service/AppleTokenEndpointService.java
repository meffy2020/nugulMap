package com.neogulmap.neogul_map.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.BusinessBaseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AppleTokenEndpointService {

    private final HttpClient httpClient;
    private final URI tokenEndpoint;
    private final URI revokeEndpoint;
    private final AppleClientSecretService clientSecretService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AppleTokenEndpointService(
            @Value("${app.oauth2.apple.revocation.token-endpoint:https://appleid.apple.com/auth/token}") String tokenEndpoint,
            @Value("${app.oauth2.apple.revocation.revoke-endpoint:https://appleid.apple.com/auth/revoke}") String revokeEndpoint,
            AppleClientSecretService clientSecretService,
            ObjectMapper objectMapper
    ) {
        this(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                URI.create(tokenEndpoint),
                URI.create(revokeEndpoint),
                clientSecretService,
                objectMapper
        );
    }

    AppleTokenEndpointService(
            HttpClient httpClient,
            URI tokenEndpoint,
            URI revokeEndpoint,
            AppleClientSecretService clientSecretService,
            ObjectMapper objectMapper
    ) {
        this.httpClient = httpClient;
        this.tokenEndpoint = tokenEndpoint;
        this.revokeEndpoint = revokeEndpoint;
        this.clientSecretService = clientSecretService;
        this.objectMapper = objectMapper;
    }

    public TokenGrant exchangeAuthorizationCode(String authorizationCode) {
        requireEnabled();
        if (authorizationCode == null || authorizationCode.isBlank()) {
            throw new BusinessBaseException(ErrorCode.REQUIRED_FIELD_MISSING, "Apple authorization code가 필요합니다.");
        }

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("client_id", clientSecretService.getClientId());
        fields.put("client_secret", clientSecretService.createClientSecret());
        fields.put("code", authorizationCode);
        fields.put("grant_type", "authorization_code");

        HttpResponse<String> response = post(tokenEndpoint, fields, ErrorCode.INVALID_FORMAT);
        if (response.statusCode() != 200) {
            throw new BusinessBaseException(
                    response.statusCode() >= 500 ? ErrorCode.EXTERNAL_SERVICE_ERROR : ErrorCode.INVALID_FORMAT,
                    "Apple authorization code를 검증할 수 없습니다."
            );
        }

        try {
            JsonNode body = objectMapper.readTree(response.body());
            String refreshToken = requiredText(body, "refresh_token");
            String identityToken = requiredText(body, "id_token");
            return new TokenGrant(refreshToken, identityToken);
        } catch (IOException | IllegalArgumentException e) {
            throw new BusinessBaseException(
                    ErrorCode.EXTERNAL_SERVICE_ERROR,
                    "Apple token 응답 형식을 확인할 수 없습니다."
            );
        }
    }

    public void revokeRefreshToken(String refreshToken) {
        requireEnabled();
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessBaseException(
                    ErrorCode.ACCOUNT_REVOCATION_REQUIRED,
                    "Apple refresh token이 없어 연결을 해제할 수 없습니다."
            );
        }

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("client_id", clientSecretService.getClientId());
        fields.put("client_secret", clientSecretService.createClientSecret());
        fields.put("token", refreshToken);
        fields.put("token_type_hint", "refresh_token");

        HttpResponse<String> response = post(revokeEndpoint, fields, ErrorCode.ACCOUNT_REVOCATION_REQUIRED);
        if (response.statusCode() != 200) {
            throw new BusinessBaseException(
                    ErrorCode.ACCOUNT_REVOCATION_REQUIRED,
                    "Apple 계정 연결 해제를 확인할 수 없습니다."
            );
        }
    }

    private HttpResponse<String> post(URI endpoint, Map<String, String> fields, ErrorCode errorCode) {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formEncode(fields)))
                .build();
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessBaseException(errorCode, "Apple 서버 요청이 중단되었습니다.");
        } catch (IOException e) {
            throw new BusinessBaseException(errorCode, "Apple 서버에 연결할 수 없습니다.");
        }
    }

    private void requireEnabled() {
        if (!clientSecretService.isEnabled()) {
            throw new BusinessBaseException(
                    ErrorCode.ACCOUNT_REVOCATION_REQUIRED,
                    "Apple token 교환과 연결 해제가 구성되지 않았습니다."
            );
        }
    }

    private static String formEncode(Map<String, String> fields) {
        return fields.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String requiredText(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing Apple token field: " + field);
        }
        return value.asText();
    }

    public record TokenGrant(String refreshToken, String identityToken) {}
}
