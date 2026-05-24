package com.securebank.security.ratelimit;

/**
 * Strategy for deciding whether a request identified by {@code key} (typically a client IP)
 * may proceed. Implementations are token buckets backed either by in-process memory or Redis.
 */
public interface RateLimiter {

    /**
     * Attempts to consume a single token for the given key.
     *
     * @return {@code true} if a token was available (request allowed), {@code false} if throttled.
     */
    boolean tryAcquire(String key);
}
