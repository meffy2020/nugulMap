package com.neogulmap.neogul_map.controller;

import com.neogulmap.neogul_map.config.annotation.CurrentUser;
import com.neogulmap.neogul_map.config.exceptionHandling.GlobalExceptionHandler;
import com.neogulmap.neogul_map.config.security.PublicInsightRateLimiter;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.dto.recommendation.PlaceProvider;
import com.neogulmap.neogul_map.dto.recommendation.RecommendationDataStatus;
import com.neogulmap.neogul_map.dto.recommendation.RecommendationIntent;
import com.neogulmap.neogul_map.dto.recommendation.RecommendationPersonalizationMode;
import com.neogulmap.neogul_map.dto.recommendation.RecommendationPlaceResponse;
import com.neogulmap.neogul_map.dto.recommendation.RecommendationReasonCode;
import com.neogulmap.neogul_map.service.recommendation.RecommendationDataService;
import com.neogulmap.neogul_map.service.recommendation.RecommendationQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecommendationControllerTest {

    private final RecommendationQueryService queryService = mock(RecommendationQueryService.class);
    private final RecommendationDataService dataService = mock(RecommendationDataService.class);
    private final PublicInsightRateLimiter rateLimiter = mock(PublicInsightRateLimiter.class);
    private final User currentUser = User.builder().id(7L).build();
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new RecommendationController(queryService, dataService, rateLimiter))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(currentUserResolver())
            .build();

    @Test
    void getPlacesExposesStablePeroRecommendationEnvelope() throws Exception {
        when(rateLimiter.tryAcquire(org.mockito.ArgumentMatchers.any(), eq(1)))
                .thenReturn(new PublicInsightRateLimiter.Decision(true, 0));
        when(queryService.recommendPlaces(
                eq(currentUser),
                eq(RecommendationIntent.CAFE),
                eq(37.5446),
                eq(127.0557),
                eq(2_500),
                eq(8)
        )).thenReturn(new RecommendationPlaceResponse(
                "req-1",
                "pero-context-v1",
                RecommendationDataStatus.READY,
                RecommendationPersonalizationMode.PERSONALIZED,
                RecommendationIntent.CAFE,
                1,
                Instant.parse("2026-09-01T03:00:00Z"),
                List.of(new RecommendationPlaceResponse.Item(
                        "123",
                        PlaceProvider.KAKAO_LOCAL,
                        "성수 카페",
                        "카페 > 디저트카페",
                        "서울 성동구 연무장길",
                        37.5446,
                        127.0557,
                        320,
                        0.91,
                        List.of(RecommendationReasonCode.PREFERRED_TAG_MATCH),
                        "https://place.map.kakao.com/123"
                ))
        ));

        mockMvc.perform(get("/recommendations/places")
                        .param("intent", "CAFE")
                        .param("lat", "37.5446")
                        .param("lng", "127.0557")
                        .param("radiusMeters", "2500")
                        .param("limit", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("페로 맞춤 추천 조회 성공"))
                .andExpect(jsonPath("$.data.requestId").value("req-1"))
                .andExpect(jsonPath("$.data.modelVersion").value("pero-context-v1"))
                .andExpect(jsonPath("$.data.personalizationMode").value("PERSONALIZED"))
                .andExpect(jsonPath("$.data.items[0].provider").value("KAKAO_LOCAL"))
                .andExpect(jsonPath("$.data.items[0].distanceMeters").value(320))
                .andExpect(jsonPath("$.data.items[0].reasonCodes[0]").value("PREFERRED_TAG_MATCH"))
                .andExpect(jsonPath("$.data.items[0].sourceUrl").value("https://place.map.kakao.com/123"));

        verify(queryService).recommendPlaces(
                currentUser,
                RecommendationIntent.CAFE,
                37.5446,
                127.0557,
                2_500,
                8
        );
        verify(rateLimiter).tryAcquire(org.mockito.ArgumentMatchers.any(), eq(1));
    }

    @Test
    void getPlacesStopsBeforeProviderLookupWhenPublicRateLimitIsExceeded() throws Exception {
        when(rateLimiter.tryAcquire(org.mockito.ArgumentMatchers.any(), eq(1)))
                .thenReturn(new PublicInsightRateLimiter.Decision(false, 29));

        mockMvc.perform(get("/recommendations/places")
                        .param("intent", "CAFE")
                        .param("lat", "37.5446")
                        .param("lng", "127.0557"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "29"))
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(queryService);
    }

    @Test
    void dateIntentSpendsFourPermitsBeforeProviderLookup() throws Exception {
        when(rateLimiter.tryAcquire(org.mockito.ArgumentMatchers.any(), eq(4)))
                .thenReturn(new PublicInsightRateLimiter.Decision(false, 31));

        mockMvc.perform(get("/recommendations/places")
                        .param("intent", "DATE")
                        .param("lat", "37.5446")
                        .param("lng", "127.0557"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "31"));

        verify(rateLimiter).tryAcquire(org.mockito.ArgumentMatchers.any(), eq(4));
        verifyNoInteractions(queryService);
    }

    @Test
    void permitCostMatchesMaximumOutboundFanout() {
        org.assertj.core.api.Assertions.assertThat(
                RecommendationController.recommendationPermitCost(RecommendationIntent.CAFE)
        ).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(
                RecommendationController.recommendationPermitCost(RecommendationIntent.RESTAURANT)
        ).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(
                RecommendationController.recommendationPermitCost(RecommendationIntent.DATE)
        ).isEqualTo(4);
    }

    @Test
    void invalidIntentReturnsValidationErrorWithoutCallingProvider() throws Exception {
        mockMvc.perform(get("/recommendations/places")
                        .param("intent", "COFFEE")
                        .param("lat", "37.5446")
                        .param("lng", "127.0557"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("V001"));

        verifyNoInteractions(rateLimiter, queryService);
    }

    @Test
    void missingCoordinatesReturnValidationErrorWithoutCallingProvider() throws Exception {
        mockMvc.perform(get("/recommendations/places")
                        .param("intent", "CAFE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("V001"));

        verifyNoInteractions(rateLimiter, queryService);
    }

    private HandlerMethodArgumentResolver currentUserResolver() {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(CurrentUser.class);
            }

            @Override
            public Object resolveArgument(
                    MethodParameter parameter,
                    ModelAndViewContainer mavContainer,
                    NativeWebRequest webRequest,
                    WebDataBinderFactory binderFactory
            ) {
                return currentUser;
            }
        };
    }
}
