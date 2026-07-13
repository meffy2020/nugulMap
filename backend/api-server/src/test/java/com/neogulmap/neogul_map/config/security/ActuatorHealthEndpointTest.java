package com.neogulmap.neogul_map.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.storage.type=local",
        "management.endpoint.health.show-details=never",
        "external.insights.hotplace-warmup.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ActuatorHealthEndpointTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void healthEndpointIsAvailableWithoutAuthenticationAndHidesDetails() throws Exception {
        mockMvc.perform(get("/api/actuator/health").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }
}
