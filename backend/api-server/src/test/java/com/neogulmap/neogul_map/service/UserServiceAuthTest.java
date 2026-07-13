package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.config.security.oauth.OAuth2UserCustomService;
import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.dto.UserRequest;
import com.neogulmap.neogul_map.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceAuthTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReviewContentPolicy contentPolicy;

    @Mock
    private AppleRefreshTokenCipher appleRefreshTokenCipher;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("신규 OAuth 사용자를 생성하고 저장 후 재조회한다")
    void processOAuth2User_NewUser() {
        // Given
        String email = "new@nugul.com";
        String providerId = "google123";
        String provider = "google";

        OAuth2UserCustomService.CustomOAuth2User mockOAuth2User = mock(OAuth2UserCustomService.CustomOAuth2User.class);
        when(mockOAuth2User.getEmail()).thenReturn(email);
        when(mockOAuth2User.getName()).thenReturn(providerId);
        when(mockOAuth2User.getRegistrationId()).thenReturn(provider);

        User[] savedUser = new User[1];
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            savedUser[0] = user;
            return user;
        });
        when(userRepository.findById(1L)).thenAnswer(invocation -> Optional.of(savedUser[0]));

        // When
        User result = userService.processOAuth2User(mockOAuth2User);

        // Then
        assertNotNull(result);
        assertEquals(email, result.getEmail());
        assertEquals(providerId, result.getOauthId());
        assertEquals(provider, result.getOauthProvider());
        assertNotNull(result.getCreatedAt());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("기존 OAuth 사용자는 OAuth 정보를 갱신하고 저장 후 재조회한다")
    void processOAuth2User_ExistingUserUpdatesOAuthFields() {
        // Given
        String email = "existing@nugul.com";
        String providerId = "naver456";
        String provider = "naver";

        User existingUser = User.builder()
                .id(1L)
                .email(email)
                .oauthId("google123")
                .oauthProvider("google")
                .build();

        OAuth2UserCustomService.CustomOAuth2User mockOAuth2User = mock(OAuth2UserCustomService.CustomOAuth2User.class);
        when(mockOAuth2User.getEmail()).thenReturn(email);
        when(mockOAuth2User.getName()).thenReturn(providerId);
        when(mockOAuth2User.getRegistrationId()).thenReturn(provider);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(existingUser)).thenReturn(existingUser);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));

        // When
        User result = userService.processOAuth2User(mockOAuth2User);

        // Then
        assertNotNull(result);
        assertEquals(email, result.getEmail());
        assertEquals(providerId, result.getOauthId());
        assertEquals(provider, result.getOauthProvider());
        verify(userRepository, times(1)).save(existingUser);
    }

    @Test
    @DisplayName("공개 닉네임은 콘텐츠 정책을 통과해야 한다")
    void updateUserRejectsObjectionableNickname() {
        User existingUser = User.builder()
                .id(1L)
                .nickname("기존너굴")
                .build();
        UserRequest request = UserRequest.builder()
                .nickname("F.U C-K")
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        org.mockito.Mockito.doThrow(new ValidationException(ErrorCode.REVIEW_CONTENT_REJECTED))
                .when(contentPolicy)
                .ensureAllowed("F.U C-K");

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> userService.updateUser(1L, request)
        );
        assertEquals(ErrorCode.NICKNAME_CONTENT_REJECTED, exception.getErrorCode());
        assertEquals("기존너굴", existingUser.getNickname());
    }

    @Test
    @DisplayName("Apple 이름이 콘텐츠 정책에 맞지 않으면 로그인은 유지하고 프로필 설정을 요구한다")
    void processAppleUserDropsObjectionableProviderNickname() {
        when(appleRefreshTokenCipher.encrypt("refresh-token")).thenReturn("encrypted-token");
        when(userRepository.findByOauthProviderAndOauthId("apple", "apple-subject"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("apple@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.doThrow(new ValidationException(ErrorCode.REVIEW_CONTENT_REJECTED))
                .when(contentPolicy)
                .ensureAllowed("F.U C-K");

        User result = userService.processAppleUser(
                "apple-subject",
                "apple@example.com",
                " F.U C-K ",
                "refresh-token"
        );

        assertNull(result.getNickname());
        assertEquals("apple", result.getOauthProvider());
    }
}
