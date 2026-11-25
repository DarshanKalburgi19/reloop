package com.reloop.product.health;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reloop.product.config.SecurityConfig;
import com.reloop.product.security.JwtService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Slice test: loads only the web layer (controllers) and replaces
 * {@link HealthService} with a Mockito mock. This proves the controller
 * delegates correctly without starting the database.
 *
 * <p>{@code @WebMvcTest} auto-configures Spring Security but does not scan plain
 * {@code @Configuration} classes, so the real {@link SecurityConfig} (and the
 * {@link JwtService} it needs) are imported explicitly. This also proves
 * {@code /api/health} stays public under the production security rules.</p>
 */
@WebMvcTest(HealthController.class)
@Import({SecurityConfig.class, JwtService.class})
class HealthControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HealthService healthService;

    @Test
    void healthReturnsWhatTheServiceProvides() throws Exception {
        when(healthService.health())
                .thenReturn(Map.of("service", "product-service", "status", "UP"));

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("product-service"))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void healthServiceIsCalledExactlyOnce() throws Exception {
        when(healthService.health())
                .thenReturn(Map.of("service", "product-service", "status", "UP"));

        mockMvc.perform(get("/api/health"));

        verify(healthService, times(1)).health();
    }
}
