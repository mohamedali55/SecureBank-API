package com.securebank.security.ratelimit;

import com.securebank.config.RateLimitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Distributed token-bucket rate limiter backed by Redis. The check-refill-consume sequence runs
 * inside a single Lua script, so it is atomic across all application instances sharing the Redis.
 *
 * <p><b>Fail-open:</b> if Redis is unreachable or errors, the request is allowed through (and the
 * failure is logged). For an availability-sensitive system, a cache outage must not lock every
 * user out of authentication; the trade-off is that the limit is not enforced during the outage.
 */
public class RedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);
    private static final String KEY_PREFIX = "ratelimit:";

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;
    private final RedisScript<Long> script;

    public RedisRateLimiter(StringRedisTemplate redisTemplate, RateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("redis/rate_limiter.lua")));
        redisScript.setResultType(Long.class);
        this.script = redisScript;
    }

    @Override
    public boolean tryAcquire(String key) {
        long refillPeriodMillis = TimeUnit.SECONDS.toMillis(properties.getRefillPeriodSeconds());
        try {
            Long allowed = redisTemplate.execute(script, List.of(KEY_PREFIX + key),
                    Integer.toString(properties.getCapacity()),
                    Integer.toString(properties.getRefillTokens()),
                    Long.toString(refillPeriodMillis),
                    Long.toString(System.currentTimeMillis()));
            return allowed != null && allowed == 1L;
        } catch (RuntimeException ex) {
            log.warn("Redis rate limiter unavailable; failing open for key '{}': {}", key, ex.getMessage());
            return true;
        }
    }
}
