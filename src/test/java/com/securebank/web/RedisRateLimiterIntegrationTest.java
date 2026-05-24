package com.securebank.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securebank.security.ratelimit.RateLimiter;
import com.securebank.security.ratelimit.RedisRateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Verifies the Redis-backed rate limiter against a real Redis (Testcontainers). The atomic Lua
 * token bucket must throttle exactly like the in-memory one. Skips cleanly when Docker isn't
 * reachable by the Java client; runs in CI where Docker is available.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "security.rate-limit.enabled=true",
        "security.rate-limit.backend=redis",
        "security.rate-limit.capacity=3",
        "security.rate-limit.refill-tokens=3",
        "security.rate-limit.refill-period-seconds=3600"
})
class RedisRateLimiterIntegrationTest {

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RateLimiter rateLimiter;

    @Test
    @DisplayName("the active limiter is the Redis-backed implementation")
    void wiresRedisBackend() {
        assertThat(rateLimiter).isInstanceOf(RedisRateLimiter.class);
    }

    @Test
    @DisplayName("[Redis] throttles after capacity via the atomic Lua token bucket")
    void throttlesViaRedis() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("username", "ghost", "password", "wrongpassword"));
        String clientIp = "192.0.2.50";

        int passed = 0;
        int limited = 0;
        for (int i = 0; i < 6; i++) {
            int status = mockMvc.perform(post("/api/auth/login")
                            .header("X-Forwarded-For", clientIp)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn().getResponse().getStatus();
            if (status == 429) {
                limited++;
            } else {
                passed++;
            }
        }

        assertThat(passed).as("capacity requests allowed").isEqualTo(3);
        assertThat(limited).as("excess requests throttled by Redis").isEqualTo(3);
    }
}
