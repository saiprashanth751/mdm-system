package com.moveinsync.mdm.service;

import com.moveinsync.mdm.entity.AuditLog;
import com.moveinsync.mdm.enums.ActorType;
import com.moveinsync.mdm.enums.AuditEntityType;
import com.moveinsync.mdm.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Centralized audit logging service.
 * Used by all other services to record actions — append-only, never updated.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void logEvent(AuditEntityType entityType, UUID entityId, String action,
            String fromState, String toState,
            UUID actorId, ActorType actorType,
            Map<String, Object> metadata) {

        AuditLog auditLog = AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .fromState(fromState)
                .toState(toState)
                .actorId(actorId)
                .actorType(actorType)
                .metadata(metadata)
                .build();

        auditLogRepository.save(auditLog);

        log.info("AUDIT: [{}] {} on entity {} | {} → {} | actor: {} ({})",
                entityType, action, entityId, fromState, toState, actorId, actorType);
    }

    /**
     * Simplified log method for actions without state transitions.
     */
    @Transactional
    public void logAction(AuditEntityType entityType, UUID entityId, String action,
            UUID actorId, ActorType actorType, Map<String, Object> metadata) {
        logEvent(entityType, entityId, action, null, null, actorId, actorType, metadata);
    }
}
