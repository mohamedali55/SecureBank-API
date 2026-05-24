package com.securebank.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Verifies the auth-endpoint rate limiter. A dedicated context with a tiny bucket
 * (capacity 3, no refill during the test) makes the behaviour deterministic. Each test method
 * uses a distinct client IP (via X-Forwarded-For) so the per-IP buckets don't interfere.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "security.rate-limit.enabled=true",
        "security.rate-limit.capacity=3",
        "security.rate-limit.refill-tokens=3",
        "security.rate-limit.refill-period-seconds=3600"
})
class RateLimiterFilterTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("after the bucket is drained, further auth requests get 429 with Retry-After")
    void blocksWhenBucketExhausted() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("username", "ghost", "password", "wrongpassword"));
        String clientIp = "203.0.113.7";

        int passed = 0;
        int limited = 0;
        MvcResult lastLimited = null;
        for (int i = 0; i < 6; i++) {
            MvcResult result = mockMvc.perform(post("/api/auth/login")
                            .header("X-Forwarded-For", clientIp)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn();
            int status = result.getResponse().getStatus();
            if (status == 429) {
                limited++;
                lastLimited = result;
            } else {
                passed++; // reached the controller (401 for bad credentials)
            }
        }

        // capacity = 3 -> first 3 reach the app, the remaining 3 are throttled
        assertThat(passed).as("requests allowed through equals the bucket capacity").isEqualTo(3);
        assertThat(limited).as("requests beyond capacity are rejected").isEqualTo(3);
        assertThat(lastLimited).isNotNull();
        assertThat(lastLimited.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("3600");
        assertThat(lastLimited.getResponse().getContentAsString()).contains("Too many requests");
    }

    @Test
    @DisplayName("different client IPs have independent buckets")
    void bucketsArePerClientIp() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("username", "ghost", "password", "wrongpassword"));

        // IP A drains its bucket (3 ok, then throttled)
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login").header("X-Forwarded-For", "198.51.100.1")
                    .contentType(MediaType.APPLICATION_JSON).content(body));
        }
        int aAfter = mockMvc.perform(post("/api/auth/login").header("X-Forwarded-For", "198.51.100.1")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();

        // A fresh IP still has a full bucket
        int bFirst = mockMvc.perform(post("/api/auth/login").header("X-Forwarded-For", "198.51.100.2")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();

        assertThat(aAfter).as("IP A is throttled after its bucket drains").isEqualTo(429);
        assertThat(bFirst).as("IP B is unaffected by IP A").isNotEqualTo(429);
    }
}
