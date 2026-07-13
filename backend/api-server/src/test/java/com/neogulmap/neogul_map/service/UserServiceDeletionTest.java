package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.config.exceptionHandling.exception.BusinessBaseException;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.domain.Zone;
import com.neogulmap.neogul_map.domain.enums.ImageType;
import com.neogulmap.neogul_map.repository.UserRepository;
import com.neogulmap.neogul_map.repository.ZoneRepository;
import com.neogulmap.neogul_map.repository.ZoneReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceDeletionTest {

    @Mock private UserRepository userRepository;
    @Mock private ZoneRepository zoneRepository;
    @Mock private ZoneReviewRepository zoneReviewRepository;
    @Mock private ImageService imageService;
    @Mock private LinkedAccountRevocationService linkedAccountRevocationService;
    @Mock private AppleRefreshTokenCipher appleRefreshTokenCipher;
    @InjectMocks private UserService userService;

    @Test
    void appleRevocationFailurePreservesAccountSoDeletionCanBeRetried() {
        User appleUser = User.builder()
                .id(1L)
                .oauthProvider("apple")
                .oauthId("apple-sub")
                .appleRefreshTokenCiphertext("encrypted-token")
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(appleUser));
        when(zoneRepository.findByCreatorId(1L)).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new BusinessBaseException(
                        com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode.ACCOUNT_REVOCATION_REQUIRED,
                        "Apple 연결 해제를 완료할 수 없습니다."
                ))
                .doNothing()
                .when(linkedAccountRevocationService).revokeBeforeDeletion(appleUser);

        assertThatThrownBy(() -> userService.deleteUser(1L))
                .isInstanceOf(BusinessBaseException.class)
                .hasMessageContaining("Apple 연결 해제를 완료할 수 없습니다.");

        verify(userRepository, never()).deleteById(1L);
        verify(zoneReviewRepository, never()).deleteByAuthorId(1L);
        verify(zoneRepository, never()).findByCreatorId(1L);

        UserService.AccountDeletionResult result = userService.deleteUser(1L);

        assertThat(result.manualAppleRevocationRequired()).isFalse();
        verify(linkedAccountRevocationService, times(2)).revokeBeforeDeletion(appleUser);
        verify(userRepository).deleteById(1L);
        verify(zoneReviewRepository).deleteByAuthorId(1L);
        verify(zoneRepository).findByCreatorId(1L);
        verify(imageService, never()).deleteImage(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unrelatedRevocationFailureStillStopsDeletion() {
        User appleUser = User.builder()
                .id(4L)
                .oauthProvider("apple")
                .oauthId("apple-sub")
                .appleRefreshTokenCiphertext("encrypted-token")
                .build();
        when(userRepository.findById(4L)).thenReturn(Optional.of(appleUser));
        org.mockito.Mockito.doThrow(new BusinessBaseException(
                com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode.INTERNAL_SERVER_ERROR,
                "unexpected local failure"
        )).when(linkedAccountRevocationService).revokeBeforeDeletion(appleUser);

        assertThatThrownBy(() -> userService.deleteUser(4L))
                .isInstanceOf(BusinessBaseException.class)
                .hasMessageContaining("unexpected local failure");

        verify(userRepository, never()).deleteById(4L);
        verify(zoneRepository, never()).findByCreatorId(4L);
    }

    @Test
    void accountDeletionRemovesOwnedZonesAndImagesBeforeDeletingTheUser() {
        User kakaoUser = User.builder()
                .id(2L)
                .oauthProvider("kakao")
                .oauthId("kakao-id")
                .profileImage("profile.jpg")
                .build();
        Zone firstZone = Zone.builder().id(10).creator(kakaoUser).image("zone-10.jpg").build();
        Zone secondZone = Zone.builder().id(11).creator(kakaoUser).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(kakaoUser));
        when(zoneRepository.findByCreatorId(2L)).thenReturn(List.of(firstZone, secondZone));

        userService.deleteUser(2L);

        InOrder deletionOrder = inOrder(
                linkedAccountRevocationService,
                zoneReviewRepository,
                zoneRepository,
                imageService,
                userRepository
        );
        deletionOrder.verify(linkedAccountRevocationService).revokeBeforeDeletion(kakaoUser);
        deletionOrder.verify(zoneReviewRepository).deleteByAuthorId(2L);
        deletionOrder.verify(zoneRepository).findByCreatorId(2L);
        deletionOrder.verify(imageService).deleteImage("zone-10.jpg", ImageType.ZONE);
        deletionOrder.verify(zoneRepository).deleteAll(List.of(firstZone, secondZone));
        deletionOrder.verify(imageService).deleteImage("profile.jpg", ImageType.PROFILE);
        deletionOrder.verify(userRepository).deleteById(2L);
    }

    @Test
    void legacyAppleAccountIsDeletedAndSignalsManualAppleRevocation() {
        User legacyAppleUser = User.builder()
                .id(3L)
                .oauthProvider("apple")
                .oauthId("legacy-apple-id")
                .build();
        when(userRepository.findById(3L)).thenReturn(Optional.of(legacyAppleUser));
        when(zoneRepository.findByCreatorId(3L)).thenReturn(List.of());

        UserService.AccountDeletionResult result = userService.deleteUser(3L);

        assertThat(result.manualAppleRevocationRequired()).isTrue();
        verify(userRepository).deleteById(3L);
        verify(zoneReviewRepository).deleteByAuthorId(3L);
    }
}
