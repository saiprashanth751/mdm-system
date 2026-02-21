-- =============================================
-- V7: Create audit_logs table
-- Append-only, immutable audit trail
-- Every action in the system is recorded here
-- =============================================

CREATE TABLE audit_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(50) NOT NULL,
    entity_id   UUID NOT NULL,
    action      VARCHAR(100) NOT NULL,
    from_state  VARCHAR(50),
    to_state    VARCHAR(50),
    actor_id    UUID,
    actor_type  VARCHAR(20) CHECK (actor_type IN ('ADMIN', 'DEVICE', 'SYSTEM')),
    metadata    JSONB,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Primary query pattern: get all events for a specific entity
CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_created_at ON audit_logs(created_at);
CREATE INDEX idx_audit_actor ON audit_logs(actor_id);
