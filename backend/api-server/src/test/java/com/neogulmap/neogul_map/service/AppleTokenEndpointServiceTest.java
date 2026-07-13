package com.neogulmap.neogul_map.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppleTokenEndpointServiceTest {

    @Test
    void exchangesAuthorizationCodeForRefreshAndIdentityTokens() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/auth/token", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"refresh_token\":\"refresh-123\",\"id_token\":\"identity-123\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            AppleTokenEndpointService service = serviceFor(server);

            AppleTokenEndpointService.TokenGrant grant = service.exchangeAuthorizationCode("auth code/+123");

            assertThat(grant.refreshToken()).isEqualTo("refresh-123");
            assertThat(grant.identityToken()).isEqualTo("identity-123");
            assertThat(requestBody.get())
                    .contains("client_id=com.nugulmap.native")
                    .contains("client_secret=client-secret")
                    .contains("code=auth+code%2F%2B123")
                    .contains("grant_type=authorization_code");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void revokesRefreshTokenUsingAppleRevokeContract() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/auth/revoke", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            AppleTokenEndpointService service = serviceFor(server);

            service.revokeRefreshToken("refresh/+123");

            assertThat(requestBody.get())
                    .contains("client_id=com.nugulmap.native")
                    .contains("client_secret=client-secret")
                    .contains("token=refresh%2F%2B123")
                    .contains("token_type_hint=refresh_token");
        } finally {
            server.stop(0);
        }
    }

    private AppleTokenEndpointService serviceFor(HttpServer server) {
        AppleClientSecretService clientSecretService = mock(AppleClientSecretService.class);
        when(clientSecretService.isEnabled()).thenReturn(true);
        when(clientSecretService.getClientId()).thenReturn("com.nugulmap.native");
        when(clientSecretService.createClientSecret()).thenReturn("client-secret");
        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        return new AppleTokenEndpointService(
                HttpClient.newHttpClient(),
                base.resolve("/auth/token"),
                base.resolve("/auth/revoke"),
                clientSecretService,
                new ObjectMapper()
        );
    }
}
