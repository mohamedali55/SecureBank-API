package com.securebank.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Strongly-typed binding for the {@code security.jwt.*} properties.
 */
@Validated
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {

    /** HMAC signing secret. Must be at least 32 characters (256 bits) for HS256. */
    @NotBlank
    private String secret;

    /** Token lifetime in milliseconds. */
    @Positive
    private long expirationMs = 3_600_000L;

    /** Token issuer claim. */
    @NotBlank
    private String issuer = "securebank-api";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
