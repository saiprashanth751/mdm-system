-- =============================================
-- V1: Create admins table
-- Stores admin users who operate the MDM system
-- =============================================

CREATE TABLE admins (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username    VARCHAR(100) UNIQUE NOT NULL,
    password    VARCHAR(255) NOT NULL,
    role        VARCHAR(50) NOT NULL CHECK (role IN ('SUPER_ADMIN', 'RELEASE_ENGINEER', 'OPS_VIEWER')),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_admins_username ON admins(username);
