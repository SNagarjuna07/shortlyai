-- changeset shortlyai:1
-- Core URL table — every shortened URL lives here
CREATE TABLE urls (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug          VARCHAR(20)   NOT NULL UNIQUE,             -- Base62 short code e.g. "aB3xK2p"
    original_url  TEXT          NOT NULL,                    -- full destination URL
    user_id       UUID          NOT NULL,                    -- owner (no FK — cross-service boundary)
    title         VARCHAR(255),                              -- AI-generated or user-provided
    category      VARCHAR(100),                              -- AI classification result
    is_safe       BOOLEAN       NOT NULL DEFAULT TRUE,       -- AI safety check result
    click_count   BIGINT        NOT NULL DEFAULT 0,          -- denormalized for fast reads
    expires_at    TIMESTAMPTZ,                               -- null = never expires
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- changeset shortlyai:2
-- Custom slugs — user-defined aliases e.g. "my-brand"
CREATE TABLE custom_slugs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    url_id      UUID         NOT NULL REFERENCES urls(id) ON DELETE CASCADE,
    slug        VARCHAR(100) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- changeset shortlyai:3
CREATE INDEX idx_urls_slug       ON urls(slug);              -- redirect lookup — hottest query
CREATE INDEX idx_urls_user_id    ON urls(user_id);           -- "my URLs" listing
CREATE INDEX idx_urls_expires_at ON urls(expires_at)
    WHERE expires_at IS NOT NULL;                            -- partial index — only expiring URLs