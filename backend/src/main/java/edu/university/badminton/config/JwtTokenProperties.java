package edu.university.badminton.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for cryptographic JWT token generation and validation.
 */
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtTokenProperties {

    /**
     * Secret key used for signing and verifying HMAC-SHA256 JWT tokens.
     * Must be at least 256 bits (32 bytes). In production, configure via JWT_SECRET environment variable.
     */
    private String secret = "university-badminton-court-booking-jwt-secret-key-must-be-at-least-256-bits-long-for-hmac-sha256";

    /**
     * Token validity duration in hours.
     */
    private int expiryHours = 24;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public int getExpiryHours() {
        return expiryHours;
    }

    public void setExpiryHours(int expiryHours) {
        this.expiryHours = expiryHours;
    }
}
