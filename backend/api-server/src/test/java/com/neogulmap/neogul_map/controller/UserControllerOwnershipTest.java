package com.neogulmap.neogul_map.controller;

import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.BusinessBaseException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.service.ImageService;
import com.neogulmap.neogul_map.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserControllerOwnershipTest {

    @Mock private UserService userService;
    @Mock private ImageService imageService;
    @InjectMocks private UserController controller;

    @Test
    void authenticatedUserCannotReadAnotherUsersPrivateProfile() {
        var response = controller.getUser(2L, User.builder().id(1L).build());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(userService, never()).getUser(2L);
    }

    @Test
    void authenticatedUserCannotUpdateAnotherUsersProfile() {
        var response = controller.updateUser(
                2L,
                "{\"nickname\":\"attacker\"}",
                null,
                User.builder().id(1L).build()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(userService, never()).updateUser(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
        verify(imageService, never()).processImage(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void authenticatedUserCannotReplaceAnotherUsersProfileImage() {
        var response = controller.updateProfileImage(2L, null, User.builder().id(1L).build());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(userService, never()).updateProfileImage(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
        verify(imageService, never()).processImage(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void profileSetupFailureDoesNotExposeInternalExceptionMessage() {
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .oauthId("provider-id")
                .oauthProvider("google")
                .nickname(null)
                .build();
        doThrow(new IllegalStateException("MYSQL_PASSWORD=do-not-expose"))
                .when(userService)
                .updateUser(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any());

        var response = controller.completeProfile(user, "너굴", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().toString()).doesNotContain("MYSQL_PASSWORD", "do-not-expose");
    }

    @Test
    void profileSetupPropagatesNicknamePolicyViolation() {
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .oauthId("provider-id")
                .oauthProvider("google")
                .nickname(null)
                .build();
        doThrow(new ValidationException(ErrorCode.NICKNAME_CONTENT_REJECTED))
                .when(userService)
                .updateUser(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> controller.completeProfile(user, "F.U C-K", null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void profileSetupPropagatesOtherDomainFailures() {
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .oauthId("provider-id")
                .oauthProvider("google")
                .nickname(null)
                .build();
        doThrow(new BusinessBaseException(ErrorCode.USER_NOT_FOUND))
                .when(userService)
                .updateUser(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> controller.completeProfile(user, "너굴", null))
                .isInstanceOf(BusinessBaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
