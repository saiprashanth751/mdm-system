package com.moveinsync.mdm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class AuditTimelineResponse {
    private UUID entityId;
    private String entityType;
    private List<AuditEventResponse> timeline;

    @Data
    @Builder
    @AllArgsConstructor
    public static class AuditEventResponse {
        private LocalDateTime timestamp;
        private String action;
        private String fromState;
        private String toState;
        private String detail;
        private String actor;
        private String actorType;
    }
}
