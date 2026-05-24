package com.securebank.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Binding for {@code security.rate-limit.*}. Controls the per-client token bucket applied to the
 * authentication endpoints to blunt brute-force / credential-stuffing attacks.
 */
@Validated
@ConfigurationProperties(prefix = "security.rate-limit")
public class RateLimitProperties {

    /** Whether rate limiting is enforced. */
    private boolean enabled = true;

    /** Bucket backend: {@code memory} (per instance) or {@code redis} (shared across instances). */
    private String backend = "memory";

    /** Maximum burst: tokens a fresh bucket starts with (and its ceiling). */
    @Positive
    private int capacity = 10;

    /** Tokens added back each refill period. */
    @Positive
    private int refillTokens = 10;

    /** Length of the refill period, in seconds. */
    @Positive
    private long refillPeriodSeconds = 60;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBackend() {
        return backend;
    }

    public void setBackend(String backend) {
        this.backend = backend;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getRefillTokens() {
        return refillTokens;
    }

    public void setRefillTokens(int refillTokens) {
        this.refillTokens = refillTokens;
    }

    public long getRefillPeriodSeconds() {
        return refillPeriodSeconds;
    }

    public void setRefillPeriodSeconds(long refillPeriodSeconds) {
        this.refillPeriodSeconds = refillPeriodSeconds;
    }
}
