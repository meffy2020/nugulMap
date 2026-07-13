package com.neogulmap.neogul_map.config.security;

import com.neogulmap.neogul_map.config.security.jwt.JwtAuthenticationEntryPoint;
import com.neogulmap.neogul_map.config.security.jwt.JwtAuthenticationFilter;
import com.neogulmap.neogul_map.config.security.jwt.TokenProvider;
import com.neogulmap.neogul_map.config.security.oauth.OAuth2AuthorizationRequestBasedOnCookieRepository;
import com.neogulmap.neogul_map.config.security.oauth.OAuth2FailureHandler;
import com.neogulmap.neogul_map.config.security.oauth.OAuth2SuccessHandler;
import com.neogulmap.neogul_map.config.security.oauth.OAuth2UserCustomService;
import com.neogulmap.neogul_map.controller.InsightController;
import com.neogulmap.neogul_map.controller.ModerationOperatorController;
import com.neogulmap.neogul_map.controller.SupportOperatorController;
import com.neogulmap.neogul_map.controller.SupportRequestController;
import com.neogulmap.neogul_map.controller.ZoneController;
import com.neogulmap.neogul_map.dto.EventInsightResponse;
import com.neogulmap.neogul_map.dto.HotplaceResponse;
import com.neogulmap.neogul_map.dto.InsightStatusResponse;
import com.neogulmap.neogul_map.dto.SupportRequestResponse;
import com.neogulmap.neogul_map.dto.SupportRequestOperatorResponse;
import com.neogulmap.neogul_map.dto.ModerationDecisionResponse;
import com.neogulmap.neogul_map.dto.ZonePublicationDecisionResponse;
import com.neogulmap.neogul_map.service.EventInsightService;
import com.neogulmap.neogul_map.service.HotplaceService;
import com.neogulmap.neogul_map.service.InsightStatusService;
import com.neogulmap.neogul_map.service.ImageService;
import com.neogulmap.neogul_map.service.OperatorAccessGuard;
import com.neogulmap.neogul_map.service.ReviewModerationService;
import com.neogulmap.neogul_map.service.SupportRequestService;
import com.neogulmap.neogul_map.service.UserService;
import com.neogulmap.neogul_map.service.ZoneModerationService;
import com.neogulmap.neogul_map.service.ZoneService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({
        InsightController.class,
        SupportRequestController.class,
        SupportOperatorController.class,
        ModerationOperatorController.class,
        ZoneController.class
})
@ActiveProfiles("dev")
@TestPropertySource(properties = "app.swagger.enabled=false")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class PublicLaunchSecurityTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private HotplaceService hotplaceService;
    @MockitoBean private EventInsightService eventInsightService;
    @MockitoBean private InsightStatusService insightStatusService;
    @MockitoBean private PublicInsightRateLimiter publicInsightRateLimiter;
    @MockitoBean private SupportRequestService supportRequestService;
    @MockitoBean private ReviewModerationService reviewModerationService;
    @MockitoBean private ZoneModerationService zoneModerationService;
    @MockitoBean private ZoneService zoneService;
    @MockitoBean private ImageService imageService;
    @MockitoBean private OperatorAccessGuard operatorAccessGuard;
    @MockitoBean private UserService userService;
    @MockitoBean private TokenProvider tokenProvider;
    @MockitoBean private UserDetailsService userDetailsService;
    @MockitoBean private OAuth2SuccessHandler oAuth2SuccessHandler;
    @MockitoBean private OAuth2FailureHandler oAuth2FailureHandler;
    @MockitoBean private OAuth2UserCustomService oAuth2UserCustomService;
    @MockitoBean private OAuth2AuthorizationRequestBasedOnCookieRepository authorizationRequestRepository;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void insightStatusIsPublicButReviewReportsAndBlocksRequireAuthentication() throws Exception {
        when(publicInsightRateLimiter.tryAcquire(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PublicInsightRateLimiter.Decision(true, 0));
        when(insightStatusService.getStatus()).thenReturn(fallbackStatus());
        when(hotplaceService.getHotplaces(null, 8, null, null, null, null)).thenReturn(
                new HotplaceResponse(List.of(), "NO_VERIFIED_DATA", Instant.parse("2026-07-10T00:00:00Z"), List.of())
        );
        when(eventInsightService.getEvents(null, 8, null, null, null, null)).thenReturn(
                new EventInsightResponse(List.of(), "NO_VERIFIED_DATA", Instant.parse("2026-07-10T00:00:00Z"), List.of())
        );

        mockMvc.perform(get("/api/insights/status").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/insights/map").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/zones/1/reviews/2/reports")
                        .contextPath("/api")
                        .contentType("application/json")
                        .content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/zones/1/reports")
                        .contextPath("/api")
                        .contentType("application/json")
                        .content("{\"reason\":\"INACCURATE\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/users/2/block").contextPath("/api"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/zones/my").contextPath("/api"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicSupportSubmissionAndSecretGuardedOperatorPathReachTheirControllers() throws Exception {
        when(supportRequestService.create(org.mockito.ArgumentMatchers.any())).thenReturn(
                new SupportRequestResponse(
                        1L,
                        "ACCOUNT_DELETION",
                        "PENDING",
                        LocalDateTime.parse("2026-07-10T10:00:00")
                )
        );
        when(supportRequestService.getActiveRequests()).thenReturn(List.of());
        when(supportRequestService.updateStatus(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(new SupportRequestOperatorResponse(
                1L,
                "ACCOUNT_DELETION",
                "user@example.com",
                "계정 삭제 요청",
                "RESOLVED",
                LocalDateTime.parse("2026-07-10T10:00:00"),
                LocalDateTime.parse("2026-07-10T10:00:00")
        ));
        when(reviewModerationService.getPendingReports()).thenReturn(List.of());
        when(zoneModerationService.getPendingReports()).thenReturn(List.of());
        when(zoneModerationService.getPendingSubmissions()).thenReturn(List.of());
        when(reviewModerationService.decideReport(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(new ModerationDecisionResponse(1L, "REMOVE_CONTENT", "RESOLVED", true));
        when(zoneModerationService.decideReport(
                org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(new ModerationDecisionResponse(2L, "DISMISS", "DISMISSED", false));
        when(zoneModerationService.decideSubmission(
                org.mockito.ArgumentMatchers.eq(3),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(new ZonePublicationDecisionResponse(3, "PUBLISH", "PUBLISHED", false));

        mockMvc.perform(post("/api/public/support/requests")
                        .contextPath("/api")
                        .contentType("application/json")
                        .content("""
                                {
                                  "category": "ACCOUNT_DELETION",
                                  "email": "user@example.com",
                                  "message": "계정 삭제 요청",
                                  "website": ""
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.request.status").value("PENDING"));

        mockMvc.perform(get("/api/operator/support/requests")
                        .contextPath("/api")
                        .header("X-Nugul-Operator-Key", "configured-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0));

        mockMvc.perform(patch("/api/operator/support/requests/1")
                        .contextPath("/api")
                        .header("X-Nugul-Operator-Key", "configured-secret")
                        .contentType("application/json")
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.request.status").value("RESOLVED"));

        mockMvc.perform(get("/api/operator/moderation/reports")
                        .contextPath("/api")
                        .header("X-Nugul-Operator-Key", "configured-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0));

        mockMvc.perform(get("/api/operator/moderation/reports/zones")
                        .contextPath("/api")
                        .header("X-Nugul-Operator-Key", "configured-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0));

        mockMvc.perform(get("/api/operator/moderation/reports/zone-submissions")
                        .contextPath("/api")
                        .header("X-Nugul-Operator-Key", "configured-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0));

        mockMvc.perform(patch("/api/operator/moderation/reports/1")
                        .contextPath("/api")
                        .header("X-Nugul-Operator-Key", "configured-secret")
                        .contentType("application/json")
                        .content("{\"action\":\"REMOVE_CONTENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decision.contentRemoved").value(true));

        mockMvc.perform(patch("/api/operator/moderation/reports/zones/2")
                        .contextPath("/api")
                        .header("X-Nugul-Operator-Key", "configured-secret")
                        .contentType("application/json")
                        .content("{\"action\":\"DISMISS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decision.status").value("DISMISSED"));

        mockMvc.perform(patch("/api/operator/moderation/reports/zone-submissions/3")
                        .contextPath("/api")
                        .header("X-Nugul-Operator-Key", "configured-secret")
                        .contentType("application/json")
                        .content("{\"action\":\"PUBLISH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decision.status").value("PUBLISHED"));

        verify(operatorAccessGuard, times(8)).requireAccess("configured-secret");
    }

    @Test
    void productionWebOriginCanPreflightPublicSupportSubmission() throws Exception {
        mockMvc.perform(options("/api/public/support/requests")
                        .contextPath("/api")
                        .header("Origin", "https://nugulmap.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://nugulmap.com"));
    }

    @Test
    void productionWebOriginCanPreflightOperatorModerationPatch() throws Exception {
        mockMvc.perform(options("/api/operator/moderation/reports/1")
                        .contextPath("/api")
                        .header("Origin", "https://nugulmap.com")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "X-Nugul-Operator-Key, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://nugulmap.com"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("PATCH")));
    }

    @Test
    void swaggerRoutesAreDeniedWhenSwaggerIsDisabled() throws Exception {
        mockMvc.perform(get("/api/swagger-ui/index.html").contextPath("/api"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v3/api-docs").contextPath("/api"))
                .andExpect(status().isUnauthorized());
    }

    private InsightStatusResponse fallbackStatus() {
        return new InsightStatusResponse(
                false, false, false, false, false,
                "NO_VERIFIED_DATA", "NO_VERIFIED_DATA",
                InsightStatusResponse.ProviderStatus.notConfigured("not configured"),
                InsightStatusResponse.ProviderStatus.notConfigured("not configured"),
                InsightStatusResponse.ProviderStatus.notConfigured("not configured"),
                InsightStatusResponse.ProviderStatus.notConfigured("not configured"),
                new InsightStatusResponse.PopupTrendStatus(
                        false, false, 0, null, "NOT_CONFIGURED", "not configured"
                ),
                Instant.parse("2026-07-10T00:00:00Z")
        );
    }
}
