package com.securebank.security.ratelimit;

import com.securebank.config.RateLimitProperties;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Single-instance token-bucket rate limiter. Buckets live in a {@link ConcurrentHashMap} keyed by
 * client. Suitable for a single application instance; use {@link RedisRateLimiter} when running
 * more than one replica so limits are enforced globally.
 */
public class InMemoryRateLimiter implements RateLimiter {

    private final RateLimitProperties properties;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public InMemoryRateLimiter(RateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean tryAcquire(String key) {
        return buckets.computeIfAbsent(key, k -> new TokenBucket(
                        properties.getCapacity(),
                        properties.getRefillTokens(),
                        properties.getRefillPeriodSeconds()))
                .tryConsume();
    }

    /** A small, thread-safe token bucket. */
    private static final class TokenBucket {

        private final double capacity;
        private final double refillTokens;
        private final long refillPeriodNanos;
        private double tokens;
        private long lastRefillNanos;

        TokenBucket(int capacity, int refillTokens, long refillPeriodSeconds) {
            this.capacity = capacity;
            this.refillTokens = refillTokens;
            this.refillPeriodNanos = TimeUnit.SECONDS.toNanos(refillPeriodSeconds);
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1d) {
                tokens -= 1d;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            long elapsed = now - lastRefillNanos;
            if (elapsed <= 0) {
                return;
            }
            double earned = ((double) elapsed / refillPeriodNanos) * refillTokens;
            if (earned > 0) {
                tokens = Math.min(capacity, tokens + earned);
                lastRefillNanos = now;
            }
        }
    }
}
