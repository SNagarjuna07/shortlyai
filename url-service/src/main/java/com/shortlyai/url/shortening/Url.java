package com.shortlyai.url.shortening;

import jakarta.persistence.*;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

// @Entity — JPA maps this class to the "urls" table in Postgres
// @Table — explicitly names the table, never rely on JPA's default naming
@Getter
@Setter
@Entity
@Table(name = "urls")
public class Url {

    // BIGSERIAL in Postgres — auto-incremented Long in Java
    // IDENTITY strategy tells JPA to let Postgres generate the value
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The Base62-encoded short code — indexed for fast redirect lookups
    // unique = true mirrors the UNIQUE constraint in our migration
    @Column(nullable = false, unique = true, length = 20)
    private String slug;

    // Original long URL — TEXT in Postgres, no length limit
    // columnDefinition overrides JPA's default VARCHAR
    @Column(nullable = false, columnDefinition = "TEXT")
    private String originalUrl;

    // Owner's user ID — plain Long, NOT a JPA relationship
    // auth-service owns the users table — cross-service FK = anti-pattern
    @Column(nullable = false)
    private Long userId;

    // Page title — null until ai-service processes the URL
    @Column(length = 500)
    private String title;

    // AI classification result — null until ai-service responds
    @Column(length = 100)
    private String category;

    // AI safety check — defaults true, updated when ai-service responds
    @Column(nullable = false)
    private boolean isSafe = true;

    // Was this slug user-provided or system-generated via Base62?
    @Column(nullable = false)
    private boolean isCustom = false;

    // Soft delete — never hard-delete URLs, analytics still references them
    @Column(nullable = false)
    private boolean isActive = true;

    // Denormalized click counter — incremented on every redirect
    // analytics-service owns detailed click data, this is for fast reads
    @Column(nullable = false)
    private long clickCount = 0;

    // Null = never expires — set to NOW() + 30 days by default in service layer
    @Column
    private Instant expiresAt;

    // Always UTC — TIMESTAMPTZ in Postgres
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    // @PrePersist — JPA calls this automatically before every INSERT
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    // @PreUpdate — JPA calls this automatically before every UPDATE
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}