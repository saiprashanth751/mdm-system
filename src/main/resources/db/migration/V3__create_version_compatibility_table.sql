-- =============================================
-- V3: Create version_compatibility table
-- Defines allowed upgrade paths (directed graph edges)
-- Used for BFS path finding in upgrade validation
-- =============================================

CREATE TABLE version_compatibility (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    from_version_code           INT NOT NULL,
    to_version_code             INT NOT NULL,
    requires_intermediate       BOOLEAN DEFAULT FALSE,
    intermediate_version_code   INT,
    created_at                  TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_vc_from_version FOREIGN KEY (from_version_code) REFERENCES app_versions(version_code),
    CONSTRAINT fk_vc_to_version FOREIGN KEY (to_version_code) REFERENCES app_versions(version_code),
    CONSTRAINT uq_version_compatibility UNIQUE (from_version_code, to_version_code),
    CONSTRAINT chk_no_downgrade CHECK (to_version_code > from_version_code)
);

CREATE INDEX idx_vc_from_version ON version_compatibility(from_version_code);
