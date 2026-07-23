package com.oct.invoicesystem.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AUDIT-011 — the actuator must not be anonymous reconnaissance surface.
 *
 * <p>Proven in runtime with no {@code Authorization} header at all: {@code /actuator/info} returned
 * the application name and version, {@code /actuator/metrics} the full list of metric names
 * (Hikari pool, JVM, {@code http.server.requests}) and {@code /actuator/metrics/jvm.memory.used}
 * its measured value. {@code /actuator/env} and {@code /actuator/beans} were already closed.</p>
 *
 * <p>The one endpoint that must stay open is {@code /actuator/health}: the {@code oct_backend}
 * container healthcheck depends on it, so closing it would report the container unhealthy. That
 * non-regression is asserted here alongside the closure — it is the trap of this lot.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActuatorExposureTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void health_anonymous_stillReturns200_soTheContainerHealthcheckKeepsWorking() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void metrics_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void metricValue_anonymous_returns401() throws Exception {
        // The finding's sharpest evidence: an anonymous caller read the JVM memory value.
        mockMvc.perform(get("/actuator/metrics/jvm.memory.used"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void info_anonymous_returns401() throws Exception {
        // Leaked the application version — free fingerprinting.
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void metrics_asNonAdmin_returns403() throws Exception {
        // Authenticated is not enough: operating the platform is the admin's job.
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void metrics_asAdmin_returns200() throws Exception {
        // Counter-proof: the endpoint is closed, not broken. Without this, deleting the actuator
        // altogether would satisfy every assertion above.
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk());
    }
}
