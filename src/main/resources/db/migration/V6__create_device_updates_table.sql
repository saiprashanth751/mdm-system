-- =============================================
-- V6: Create device_updates table
-- Per-device update tracking (state machine)
-- One row per device per update campaign
-- =============================================

CREATE TABLE device_updates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    schedule_id     UUID NOT NULL,
    device_id       UUID NOT NULL,
    current_state   VARCHAR(50) NOT NULL DEFAULT 'SCHEDULED'
                    CHECK (current_state IN (
                        'SCHEDULED', 'NOTIFIED', 'DOWNLOAD_STARTED',
                        'DOWNLOAD_COMPLETED', 'INSTALLATION_STARTED',
                        'INSTALLATION_COMPLETED', 'FAILED'
                    )),
    failure_stage   VARCHAR(50),
    failure_reason  VARCHAR(500),
    retry_count     INT DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW(),

    CONSTRAINT fk_du_schedule FOREIGN KEY (schedule_id) REFERENCES update_schedules(id),
    CONSTRAINT fk_du_device FOREIGN KEY (device_id) REFERENCES devices(id),
    CONSTRAINT uq_schedule_device UNIQUE (schedule_id, device_id)
);

CREATE INDEX idx_du_schedule_id ON device_updates(schedule_id);
CREATE INDEX idx_du_device_id ON device_updates(device_id);
CREATE INDEX idx_du_current_state ON device_updates(current_state);
