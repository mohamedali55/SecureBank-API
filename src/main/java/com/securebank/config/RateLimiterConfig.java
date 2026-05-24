package com.securebank.config;

import com.securebank.security.ratelimit.InMemoryRateLimiter;
import com.securebank.security.ratelimit.RateLimiter;
import com.securebank.security.ratelimit.RedisRateLimiter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Chooses the rate-limiter backend from {@code security.rate-limit.backend}:
 * {@code redis} for a shared, multi-instance limit, otherwise an in-memory bucket per instance.
 */
@Configuration
public class RateLimiterConfig {

    @Bean
    @ConditionalOnProperty(name = "security.rate-limit.backend", havingValue = "redis")
    public RateLimiter redisRateLimiter(StringRedisTemplate redisTemplate, RateLimitProperties properties) {
        return new RedisRateLimiter(redisTemplate, properties);
    }

    @Bean
    @ConditionalOnMissingBean(RateLimiter.class)
    public RateLimiter inMemoryRateLimiter(RateLimitProperties properties) {
        return new InMemoryRateLimiter(properties);
    }
}
