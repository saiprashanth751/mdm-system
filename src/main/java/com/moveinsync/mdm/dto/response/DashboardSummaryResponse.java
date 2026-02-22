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
    private List<VersionHeatmap> versionHeatmap;

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
        private List<VersionCount> versionAdoption; // Gap #9: version adoption per region
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class VersionCount {
        private String version;
        private long count;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class ActiveRollout {
        private UUID scheduleId;
        private String targetVersion;
        private double progress;
        private String status;
        private long successCount; // Gap #7: success count
        private long failureCount; // Gap #7: failure count
        private double successRate; // Gap #7: success rate percentage
        private double failureRate; // Gap #7: failure rate percentage
    }

    // Gap #8: Version heatmap — cross-region/version device counts
    @Data
    @Builder
    @AllArgsConstructor
    public static class VersionHeatmap {
        private String region;
        private String version;
        private long deviceCount;
    }
}
