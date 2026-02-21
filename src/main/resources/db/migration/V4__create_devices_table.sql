-- =============================================
-- V4: Create devices table
-- Central device registry for all phones running the app
-- =============================================

CREATE TABLE devices (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    imei            VARCHAR(20) UNIQUE NOT NULL,
    app_version     VARCHAR(20) NOT NULL,
    device_os       VARCHAR(50),
    device_model    VARCHAR(100),
    region          VARCHAR(100),
    client_tag      VARCHAR(100),
    last_heartbeat  TIMESTAMP,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_devices_imei ON devices(imei);
CREATE INDEX idx_devices_region ON devices(region);
CREATE INDEX idx_devices_app_version ON devices(app_version);
CREATE INDEX idx_devices_client_tag ON devices(client_tag);
CREATE INDEX idx_devices_status ON devices(status);
CREATE INDEX idx_devices_last_heartbeat ON devices(last_heartbeat);
