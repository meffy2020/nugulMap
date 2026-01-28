package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.domain.SocialAccount;
import com.neogulmap.neogul_map.repository.UserRepository;
import com.neogulmap.neogul_map.repository.SocialAccountRepository;
import com.neogulmap.neogul_map.config.security.oauth.OAuth2UserCustomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Optional;
import java.util.Map;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceAuthTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("신규 사용자 가입 테스트 (Shadow Signup)")
    void processOAuth2User_NewUser() {
        // Given
        String email = "new@nugul.com";
        String providerId = "google123";
        String provider = "google";

        OAuth2UserCustomService.CustomOAuth2User mockOAuth2User = mock(OAuth2UserCustomService.CustomOAuth2User.class);
        when(mockOAuth2User.getEmail()).thenReturn(email);
        when(mockOAuth2User.getName()).thenReturn(providerId);
        when(mockOAuth2User.getRegistrationId()).thenReturn(provider);

        when(socialAccountRepository.findByProviderAndProviderId(provider, providerId)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArguments()[0]);

        // When
        User result = userService.processOAuth2User(mockOAuth2User);

        // Then
        assertNotNull(result);
        assertEquals(email, result.getEmail());
        verify(socialAccountRepository, times(1)).save(any(SocialAccount.class));
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("이미 가입된 이메일로 다른 소셜 로그인 시 차단 테스트")
    void processOAuth2User_BlockDuplicateEmail() {
        // Given
        String email = "existing@nugul.com";
        String providerId = "naver456";
        String provider = "naver";

        // 기존에 구글로 가입된 유저 정보 모킹
        User existingUser = User.builder()
                .id(1L)
                .email(email)
                .socialAccounts(List.of(SocialAccount.builder().provider("google").build()))
                .build();

        OAuth2UserCustomService.CustomOAuth2User mockOAuth2User = mock(OAuth2UserCustomService.CustomOAuth2User.class);
        when(mockOAuth2User.getEmail()).thenReturn(email);
        when(mockOAuth2User.getName()).thenReturn(providerId);
        when(mockOAuth2User.getRegistrationId()).thenReturn(provider);

        when(socialAccountRepository.findByProviderAndProviderId(provider, providerId)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(existingUser));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userService.processOAuth2User(mockOAuth2User);
        });

        assertTrue(exception.getMessage().contains("ALREADY_REGISTERED_WITH_google"));
        logInfo("중복 가입 차단 성공: " + exception.getMessage());
    }

    private void logInfo(String msg) {
        System.out.println("[TEST LOG] " + msg);
    }
}
