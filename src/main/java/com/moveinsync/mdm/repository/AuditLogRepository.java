package com.moveinsync.mdm.repository;

import com.moveinsync.mdm.entity.AuditLog;
import com.moveinsync.mdm.enums.AuditEntityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    // Timeline for a specific entity (device update, schedule, etc.)
    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtAsc(AuditEntityType entityType, UUID entityId);

    // All audit logs for a device (across all entity types)
    List<AuditLog> findByActorIdOrderByCreatedAtAsc(UUID actorId);

    // All audit logs related to a specific entity
    List<AuditLog> findByEntityIdOrderByCreatedAtAsc(UUID entityId);
}
