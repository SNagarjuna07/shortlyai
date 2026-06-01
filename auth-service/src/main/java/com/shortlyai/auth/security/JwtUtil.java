package com.shortlyai.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component// Spring manages this as a singleton bean
public class JwtUtil {

    // @Value — reads from application.yml jwt.secret
    // Never hardcode secrets — always inject from config
    private final SecretKey signingKey;
    private final long accessTokenExpiryMs;
    private final long refreshTokenExpiryMs;

    // Constructor injection — @Value works on constructor params too
    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiry-ms}") long accessTokenExpiryMs,
            @Value("${jwt.refresh-token-expiry-ms}") long refreshTokenExpiryMs
    ) {
        // Keys.hmacShaKeyFor — creates a type-safe HMAC key from raw bytes
        // Must be at least 256 bits (32 chars) for HS256
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiryMs = accessTokenExpiryMs;
        this.refreshTokenExpiryMs = refreshTokenExpiryMs;
    }

    // Generates short-lived access token — sent with every API request
    public String generateAccessToken(UUID userId, String email, String role) {
        return buildToken(userId, email, role, accessTokenExpiryMs);
    }

    // Generates long-lived refresh token — stored in Redis + DB
    public String generateRefreshToken(UUID userId, String email, String role) {
        return buildToken(userId, email, role, refreshTokenExpiryMs);
    }

    // Private builder — avoids duplicating token creation logic
    private String buildToken(UUID userId, String email, String role, long expiryMs) {
        Instant now = Instant.now();

        return Jwts.builder()
                // subject = who this token belongs to (userId as string)
                .subject(userId.toString())
                // custom claims — extra data embedded in token payload
                .claim("email", email)
                .claim("role", role)
                // jti = JWT ID — unique per token, used to detect replay attacks
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expiryMs)))
                // sign with HMAC-SHA256 — symmetric, same key for sign + verify
                .signWith(signingKey)
                .compact();  // serializes to "header.payload.signature" string
    }

    // Extracts all claims from token — throws if invalid or expired
    public Claims extractAllClaims(String token) {
        // parseSignedClaims verifies signature + expiry automatically
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // Convenience methods — callers get specific fields without touching Claims
    public String extractUserId(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractEmail(String token) {
        return extractAllClaims(token).get("email", String.class);
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    // Returns true only if signature valid AND not expired
    // Never throws — callers get boolean, handle accordingly
    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);  // throws on invalid/expired
            return true;
        } catch (JwtException e) {
            // Parameterized logging — never concatenate strings in log calls
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            log.warn("JWT token is null or empty: {}", e.getMessage());
            return false;
        }
    }

    // Extracts expiry — used when storing token in Redis with matching TTL
    public Instant extractExpiry(String token) {
        return extractAllClaims(token).getExpiration().toInstant();
    }
}