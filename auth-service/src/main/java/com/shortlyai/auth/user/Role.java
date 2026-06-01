package com.shortlyai.auth.user;

// Sealed — only these 3 roles exist, compiler enforces exhaustive switch
public enum Role {
    ROLE_FREE,   // default — limited URLs, no analytics
    ROLE_PRO,    // paid — unlimited URLs, full analytics
    ROLE_ADMIN   // internal — full access
}