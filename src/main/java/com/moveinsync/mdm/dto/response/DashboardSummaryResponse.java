package com.moveinsync.mdm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class DashboardSummaryResponse {
    private long totalDevices;
    private long activeDevices;
    private long inactiveDevices;
    private List<VersionDistribution> versionDistribution;
    private List<RegionBreakdown> regionBreakdown;
    private List<ActiveRollout> activeRollouts;

    @Data
    @Builder
    @AllArgsConstructor
    public static class VersionDistribution {
        private String version;
        private long count;
        private double percentage;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class RegionBreakdown {
        private String region;
        private long totalDevices;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class ActiveRollout {
        private UUID scheduleId;
        private String targetVersion;
        private double progress;
        private String status;
    }
}
