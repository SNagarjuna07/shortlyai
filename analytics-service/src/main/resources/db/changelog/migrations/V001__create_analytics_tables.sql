-- liquibase formatted sql

-- changeset shortlyai:1
-- Raw click events — one row per click, consumed from Kafka
CREATE TABLE click_events (
    id          BIGSERIAL    PRIMARY KEY,
    url_id      UUID         NOT NULL,                       -- no FK — cross-service boundary
    slug        VARCHAR(20)  NOT NULL,
    user_agent  TEXT,
    ip_hash     VARCHAR(64),                                 -- hashed IP — GDPR compliant
    referer     TEXT,
    country     VARCHAR(100),
    city        VARCHAR(100),
    clicked_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- changeset shortlyai:2
-- Hourly rollup — pre-aggregated for fast dashboard queries
CREATE TABLE click_hourly (
    id          BIGSERIAL   PRIMARY KEY,
    url_id      UUID        NOT NULL,
    hour        TIMESTAMPTZ NOT NULL,                        -- truncated to hour e.g. 2025-01-01 14:00:00
    click_count BIGINT      NOT NULL DEFAULT 0,
    UNIQUE (url_id, hour)                                    -- one row per url per hour
);

-- changeset shortlyai:3
CREATE INDEX idx_click_events_url_id     ON click_events(url_id);
CREATE INDEX idx_click_events_clicked_at ON click_events(clicked_at);
CREATE INDEX idx_click_hourly_url_id     ON click_hourly(url_id);
CREATE INDEX idx_click_hourly_hour       ON click_hourly(hour);

-- changeset shortlyai:4
ALTER TABLE click_events ALTER COLUMN url_id TYPE BIGINT USING url_id::text::bigint;