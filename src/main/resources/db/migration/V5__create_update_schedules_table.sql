-- =============================================
-- V5: Create update_schedules table
-- Admin-created update campaigns (rollout plans)
-- =============================================

CREATE TABLE update_schedules (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_by          UUID NOT NULL,
    from_version_code   INT NOT NULL,
    to_version_code     INT NOT NULL,
    target_region       VARCHAR(100),
    target_client_tag   VARCHAR(100),
    rollout_type        VARCHAR(20) NOT NULL CHECK (rollout_type IN ('IMMEDIATE', 'SCHEDULED', 'PHASED')),
    rollout_percentage  INT DEFAULT 100 CHECK (rollout_percentage BETWEEN 1 AND 100),
    scheduled_at        TIMESTAMP,
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING_APPROVAL'
                        CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'IN_PROGRESS', 'COMPLETED', 'REJECTED')),
    approved_by         UUID,
    approved_at         TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_schedule_created_by FOREIGN KEY (created_by) REFERENCES admins(id),
    CONSTRAINT fk_schedule_approved_by FOREIGN KEY (approved_by) REFERENCES admins(id),
    CONSTRAINT chk_schedule_no_downgrade CHECK (to_version_code > from_version_code)
);

CREATE INDEX idx_schedules_status ON update_schedules(status);
CREATE INDEX idx_schedules_created_by ON update_schedules(created_by);
