package com.nyasha.store.configurations;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OperationsReadinessEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthAndInfoEndpointsArePublic() throws Exception {
        MvcResult healthResponse = mockMvc.perform(get("/actuator/health"))
                .andReturn();
        assertPublicActuatorResponse(healthResponse, "/actuator/health");
        assertThat(healthResponse.getResponse().getContentAsString()).contains("\"status\":\"");

        MvcResult infoResponse = mockMvc.perform(get("/actuator/info"))
                .andReturn();
        assertPublicActuatorResponse(infoResponse, "/actuator/info");
        assertThat(infoResponse.getResponse().getContentAsString()).isNotBlank();
    }

    @Test
    void operationalManagementEndpointsRemainAdminOnly() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/metrics").with(user("buyer@example.com").roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/metrics").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray());
    }

    private void assertPublicActuatorResponse(MvcResult result, String path) {
        int status = result.getResponse().getStatus();
        assertThat(status).as("Public actuator endpoint should not require authentication: " + path)
                .isNotIn(401, 403);
    }
}
