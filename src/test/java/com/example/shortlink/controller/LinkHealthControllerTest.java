package com.example.shortlink.controller;

import com.example.shortlink.dto.request.BatchHealthCheckRequest;
import com.example.shortlink.dto.response.HealthCheckResponse;
import com.example.shortlink.dto.response.UrlHealthResult;
import com.example.shortlink.exception.GlobalExceptionHandler;
import com.example.shortlink.exception.HealthCheckBusyException;
import com.example.shortlink.service.LinkHealthService;
import com.example.shortlink.validator.ShortUrlParser;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class LinkHealthControllerTest {

    @Test
    void shouldExposeSingleHealthCheckEndpoint() throws Exception {
        StubLinkHealthService service = new StubLinkHealthService();
        MockMvc mockMvc = standaloneSetup(new LinkHealthController(service, new ShortUrlParser())).build();

        mockMvc.perform(post("/api/v1/short-links/health-check")
                        .contentType("application/json")
                        .content("{\"shortUrl\":\"http://localhost:8090/s/abc123\",\"markBroken\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shortCode").value("abc123"))
                .andExpect(jsonPath("$.data.markedBroken").value(true));
    }

    @Test
    void shouldExposeBatchHealthCheckEndpoint() throws Exception {
        StubLinkHealthService service = new StubLinkHealthService();
        MockMvc mockMvc = standaloneSetup(new LinkHealthController(service, new ShortUrlParser())).build();

        mockMvc.perform(post("/api/v1/short-links/batch-health-check")
                        .contentType("application/json")
                        .content("{\"shortUrls\":[\"http://localhost:8090/s/abc123\",\"http://localhost:8090/s/def456\"],\"markBroken\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void shouldReturnServiceUnavailableWhenProbeCapacityIsExhausted() throws Exception {
        LinkHealthService service = new LinkHealthService() {
            @Override
            public HealthCheckResponse healthCheck(String shortCode, boolean markBroken) {
                throw new HealthCheckBusyException("health check capacity is exhausted, please retry later");
            }

            @Override
            public List<HealthCheckResponse> batchHealthCheck(List<String> shortCodes, boolean markBroken) {
                throw new HealthCheckBusyException("health check capacity is exhausted, please retry later");
            }
        };
        MockMvc mockMvc = standaloneSetup(new LinkHealthController(service, new ShortUrlParser()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/api/v1/short-links/health-check")
                        .contentType("application/json")
                        .content("{\"shortUrl\":\"http://localhost:8090/s/abc123\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("HEALTH_CHECK_BUSY"));
    }

    private static final class StubLinkHealthService implements LinkHealthService {

        @Override
        public HealthCheckResponse healthCheck(String shortCode, boolean markBroken) {
            return new HealthCheckResponse(
                    shortCode,
                    true,
                    200,
                    "ok",
                    LocalDateTime.of(2026, 8, 3, 10, 0),
                    markBroken,
                    List.of(new UrlHealthResult("https://example.com", true, 200, "ok", 1)));
        }

        @Override
        public List<HealthCheckResponse> batchHealthCheck(List<String> shortCodes, boolean markBroken) {
            return shortCodes.stream()
                    .map(code -> healthCheck(code, markBroken))
                    .toList();
        }
    }
}
