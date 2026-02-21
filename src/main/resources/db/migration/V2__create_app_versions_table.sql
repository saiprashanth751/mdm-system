-- =============================================
-- V2: Create app_versions table
-- Version repository - every app build ever released
-- Immutable once published (no updated_at column)
-- =============================================

CREATE TABLE app_versions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version_code        INT UNIQUE NOT NULL,
    version_name        VARCHAR(20) NOT NULL,
    release_date        DATE,
    min_os_version      VARCHAR(20),
    max_os_version      VARCHAR(20),
    customization_tag   VARCHAR(100) DEFAULT 'GENERIC',
    is_mandatory        BOOLEAN DEFAULT FALSE,
    is_active           BOOLEAN DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_app_versions_version_code ON app_versions(version_code);
CREATE INDEX idx_app_versions_customization_tag ON app_versions(customization_tag);
