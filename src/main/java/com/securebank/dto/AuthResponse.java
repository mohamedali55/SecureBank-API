package com.securebank.dto;

public record AuthResponse(
        String token,
        String tokenType,
        long expiresInMs,
        String username) {

    public static AuthResponse bearer(String token, long expiresInMs, String username) {
        return new AuthResponse(token, "Bearer", expiresInMs, username);
    }
}
