package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.config.exceptionHandling.exception.BusinessBaseException;
import com.neogulmap.neogul_map.domain.LocationLetter;
import com.neogulmap.neogul_map.domain.LocationLetterLike;
import com.neogulmap.neogul_map.domain.LocationLetterReport;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.dto.LocationLetterCreateRequest;
import com.neogulmap.neogul_map.dto.LocationLetterNearbyResponse;
import com.neogulmap.neogul_map.dto.LocationLetterLikeResponse;
import com.neogulmap.neogul_map.dto.ReviewReportRequest;
import com.neogulmap.neogul_map.dto.ModerationDecisionRequest;
import com.neogulmap.neogul_map.dto.ModerationDecisionResponse;
import com.neogulmap.neogul_map.repository.LocationLetterReportRepository;
import com.neogulmap.neogul_map.repository.LocationLetterRepository;
import com.neogulmap.neogul_map.repository.LocationLetterLikeRepository;
import com.neogulmap.neogul_map.repository.UserBlockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import com.neogulmap.neogul_map.domain.enums.ModerationStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocationLetterServiceTest {

    private LocationLetterRepository letterRepository;
    private LocationLetterReportRepository reportRepository;
    private LocationLetterLikeRepository likeRepository;
    private UserBlockRepository userBlockRepository;
    private LocationLetterService service;

    @BeforeEach
    void setUp() {
        letterRepository = mock(LocationLetterRepository.class);
        reportRepository = mock(LocationLetterReportRepository.class);
        likeRepository = mock(LocationLetterLikeRepository.class);
        userBlockRepository = mock(UserBlockRepository.class);
        service = new LocationLetterService(
                letterRepository,
                reportRepository,
                likeRepository,
                userBlockRepository,
                new ReviewContentPolicy("blocked-term")
        );
    }

    @Test
    void nearbyLettersWorkWithoutRegisteredPlaceAndFilterDistanceAndBlocks() {
        User currentUser = user(1L);
        User visibleAuthor = user(2L);
        User blockedAuthor = user(3L);
        LocationLetter nearby = letter(10L, visibleAuthor, 37.5665, 126.9780, "오늘의 공기가 좋았어요.");
        LocationLetter farAway = letter(11L, visibleAuthor, 37.5700, 126.9780, "멀리 있는 편지");
        LocationLetter blocked = letter(12L, blockedAuthor, 37.5666, 126.9781, "차단한 사용자의 편지");

        when(userBlockRepository.findBlockedUserIdsByBlockerId(1L)).thenReturn(Set.of(3L));
        when(letterRepository.findNearbyCandidates(
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(Pageable.class)
        )).thenReturn(List.of(nearby, farAway, blocked));
        when(likeRepository.countByLetterIds(any())).thenReturn(List.<Object[]>of(new Object[]{10L, 4L}));
        when(likeRepository.findLikedLetterIds(1L, List.of(10L))).thenReturn(List.of(10L));

        LocationLetterNearbyResponse response = service.getNearby(37.5665, 126.9780, 30, currentUser);

        assertEquals(LocationLetterService.NEARBY_RADIUS_METERS, response.radiusMeters());
        assertEquals(1, response.count());
        assertEquals("오늘의 공기가 좋았어요.", response.letters().getFirst().content());
        assertEquals("사용자2", response.letters().getFirst().authorLabel());
        assertEquals(4L, response.letters().getFirst().likeCount());
        assertTrue(response.letters().getFirst().likedByMe());
        assertFalse(response.letters().getFirst().mine());
    }

    @Test
    void createAcceptsAnyValidCoordinateAndStoresOnlyCoarseLocation() {
        User author = user(7L);
        when(letterRepository.save(any(LocationLetter.class))).thenAnswer(invocation -> {
            LocationLetter saved = invocation.getArgument(0);
            saved.setId(21L);
            saved.setCreatedAt(LocalDateTime.of(2026, 9, 2, 9, 30));
            return saved;
        });

        var response = service.create(
                new LocationLetterCreateRequest(35.1795543, 129.0756416, "  이곳을 오래 기억하고 싶어요.  "),
                author
        );

        ArgumentCaptor<LocationLetter> captor = ArgumentCaptor.forClass(LocationLetter.class);
        verify(letterRepository).save(captor.capture());
        LocationLetter saved = captor.getValue();
        assertEquals(new BigDecimal("35.1796"), saved.getLatitudeBucket());
        assertEquals(new BigDecimal("129.0756"), saved.getLongitudeBucket());
        assertEquals("이곳을 오래 기억하고 싶어요.", saved.getContent());
        assertTrue(response.mine());
        assertEquals("사용자7", response.authorLabel());
        assertEquals(0L, response.likeCount());
        assertFalse(response.likedByMe());
    }

    @Test
    void createRequiresSignInButReadingDoesNot() {
        when(letterRepository.findNearbyCandidates(
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(Pageable.class)
        )).thenReturn(List.of());

        assertEquals(0, service.getNearby(37.5, 127.0, null, null).count());
        assertThrows(
                BusinessBaseException.class,
                () -> service.create(new LocationLetterCreateRequest(37.5, 127.0, "안녕"), null)
        );
        verify(letterRepository, never()).save(any(LocationLetter.class));
    }

    @Test
    void publicReadingDoesNotExposeStableAuthorId() {
        LocationLetter nearby = letter(30L, user(9L), 37.5, 127.0, "익명으로 보일 편지");
        when(letterRepository.findNearbyCandidates(
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(Pageable.class)
        )).thenReturn(List.of(nearby));

        LocationLetterNearbyResponse response = service.getNearby(37.5, 127.0, null, null);

        assertEquals(1, response.count());
        assertEquals(null, response.letters().getFirst().authorId());
        assertEquals("사용자9", response.letters().getFirst().authorLabel());
        assertEquals(0L, response.letters().getFirst().likeCount());
        assertFalse(response.letters().getFirst().likedByMe());
    }

    @Test
    void likingIsIdempotentAndReturnsThePersistedCount() {
        User liker = user(4L);
        LocationLetter target = letter(40L, user(5L), 37.5, 127.0, "좋아요 대상");
        when(letterRepository.findByIdWithAuthor(40L)).thenReturn(Optional.of(target));
        when(likeRepository.existsByLetterIdAndUserId(40L, 4L)).thenReturn(false);
        when(likeRepository.countByLetterId(40L)).thenReturn(1L);

        LocationLetterLikeResponse response = service.like(40L, liker);

        verify(likeRepository).save(any(LocationLetterLike.class));
        assertEquals(40L, response.letterId());
        assertEquals(1L, response.likeCount());
        assertTrue(response.likedByMe());
    }

    @Test
    void unlikingIsIdempotentAndReturnsThePersistedCount() {
        User liker = user(4L);
        LocationLetter target = letter(41L, user(5L), 37.5, 127.0, "좋아요 취소 대상");
        when(letterRepository.findByIdWithAuthor(41L)).thenReturn(Optional.of(target));
        when(likeRepository.countByLetterId(41L)).thenReturn(0L);

        LocationLetterLikeResponse response = service.unlike(41L, liker);

        verify(likeRepository).deleteByLetterIdAndUserId(41L, 4L);
        assertEquals(0L, response.likeCount());
        assertFalse(response.likedByMe());
    }

    @Test
    void duplicateLetterReportIsRejected() {
        User reporter = user(4L);
        LocationLetter letter = letter(44L, user(5L), 37.5, 127.0, "신고 대상");
        when(letterRepository.findByIdWithAuthor(44L)).thenReturn(Optional.of(letter));
        when(reportRepository.existsByLetterIdAndReporterId(44L, 4L)).thenReturn(true);

        assertThrows(
                BusinessBaseException.class,
                () -> service.report(44L, new ReviewReportRequest("SPAM", null), reporter)
        );
        verify(reportRepository, never()).save(any(LocationLetterReport.class));
    }

    @Test
    void operatorCanRemoveReportedLetterAndAllOfItsReports() {
        LocationLetter target = letter(51L, user(6L), 37.5, 127.0, "삭제할 편지");
        LocationLetterReport report = LocationLetterReport.builder()
                .id(61L)
                .letter(target)
                .reporter(user(7L))
                .reason(com.neogulmap.neogul_map.domain.enums.ReviewReportReason.SPAM)
                .status(ModerationStatus.PENDING)
                .createdAt(LocalDateTime.of(2026, 9, 2, 10, 0))
                .build();
        when(reportRepository.findById(61L)).thenReturn(Optional.of(report));

        ModerationDecisionResponse decision = service.decideReport(
                61L,
                new ModerationDecisionRequest("REMOVE_CONTENT")
        );

        verify(reportRepository).deleteByLetterId(51L);
        verify(letterRepository).delete(target);
        assertEquals("RESOLVED", decision.status());
        assertTrue(decision.contentRemoved());
    }

    private static User user(Long id) {
        return User.builder()
                .id(id)
                .email("user" + id + "@example.com")
                .oauthId("oauth-" + id)
                .oauthProvider("test")
                .nickname("사용자" + id)
                .build();
    }

    private static LocationLetter letter(
            Long id,
            User author,
            double latitude,
            double longitude,
            String content
    ) {
        return LocationLetter.builder()
                .id(id)
                .author(author)
                .latitudeBucket(BigDecimal.valueOf(latitude))
                .longitudeBucket(BigDecimal.valueOf(longitude))
                .content(content)
                .createdAt(LocalDateTime.of(2026, 9, 2, 9, 0))
                .build();
    }
}
