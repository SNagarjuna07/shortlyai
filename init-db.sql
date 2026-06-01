-- Runs once on first container start
-- Each service owns its own database — no cross-service DB access

CREATE DATABASE shortlyai_auth;       -- auth-service
CREATE DATABASE shortlyai_urls;       -- url-service
CREATE DATABASE shortlyai_analytics;  -- analytics-service