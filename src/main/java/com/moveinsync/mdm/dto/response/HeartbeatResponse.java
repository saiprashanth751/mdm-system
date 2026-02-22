package com.moveinsync.mdm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class HeartbeatResponse {
    private boolean acknowledged;
    private LocalDateTime lastHeartbeat;
    private PendingUpdateInfo pendingUpdate;
    private boolean versionCompliant;
    private String complianceMessage;

    @Data
    @Builder
    @AllArgsConstructor
    public static class PendingUpdateInfo {
        private String updateId;
        private String targetVersion;
        private boolean mandatory;
    }
}
