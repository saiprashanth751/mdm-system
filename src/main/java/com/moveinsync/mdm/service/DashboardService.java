package com.moveinsync.mdm.service;

import com.moveinsync.mdm.dto.response.AuditTimelineResponse;
import com.moveinsync.mdm.dto.response.DashboardSummaryResponse;
import com.moveinsync.mdm.entity.AuditLog;
import com.moveinsync.mdm.entity.UpdateSchedule;
import com.moveinsync.mdm.enums.DeviceStatus;
import com.moveinsync.mdm.enums.ScheduleStatus;
import com.moveinsync.mdm.enums.UpdateState;
import com.moveinsync.mdm.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

        private final DeviceRepository deviceRepository;
        private final DeviceUpdateRepository deviceUpdateRepository;
        private final UpdateScheduleRepository scheduleRepository;
        private final AuditLogRepository auditLogRepository;

        @Cacheable(value = "dashboard", key = "'summary'")
        @Transactional(readOnly = true)
        public DashboardSummaryResponse getDashboardSummary() {
                long totalDevices = deviceRepository.count();
                long activeDevices = deviceRepository.countByStatus(DeviceStatus.ACTIVE);
                long inactiveDevices = deviceRepository.countByStatus(DeviceStatus.INACTIVE);

                // Version distribution
                List<Object[]> versionCounts = deviceRepository.countByAppVersion();
                List<DashboardSummaryResponse.VersionDistribution> versionDistribution = versionCounts.stream()
                                .map(row -> DashboardSummaryResponse.VersionDistribution.builder()
                                                .version((String) row[0])
                                                .count((Long) row[1])
                                                .percentage(totalDevices > 0
                                                                ? Math.round(((Long) row[1]) * 1000.0 / totalDevices)
                                                                                / 10.0
                                                                : 0)
                                                .build())
                                .collect(Collectors.toList());

                // Gap #8: Version heatmap — cross-region/version counts
                List<Object[]> heatmapData = deviceRepository.countByRegionAndVersion();
                List<DashboardSummaryResponse.VersionHeatmap> versionHeatmap = heatmapData.stream()
                                .map(row -> DashboardSummaryResponse.VersionHeatmap.builder()
                                                .region((String) row[0])
                                                .version((String) row[1])
                                                .deviceCount((Long) row[2])
                                                .build())
                                .collect(Collectors.toList());

                // Gap #9: Region breakdown with version adoption per region
                Map<String, List<DashboardSummaryResponse.VersionCount>> regionVersionMap = heatmapData.stream()
                                .collect(Collectors.groupingBy(
                                                row -> (String) row[0],
                                                Collectors.mapping(
                                                                row -> DashboardSummaryResponse.VersionCount.builder()
                                                                                .version((String) row[1])
                                                                                .count((Long) row[2])
                                                                                .build(),
                                                                Collectors.toList())));

                List<Object[]> regionCounts = deviceRepository.countByRegion();
                List<DashboardSummaryResponse.RegionBreakdown> regionBreakdown = regionCounts.stream()
                                .map(row -> DashboardSummaryResponse.RegionBreakdown.builder()
                                                .region((String) row[0])
                                                .totalDevices((Long) row[1])
                                                .versionAdoption(regionVersionMap.getOrDefault(
                                                                (String) row[0], Collections.emptyList()))
                                                .build())
                                .collect(Collectors.toList());

                // Gap #7: Active rollouts with success/failure rate
                List<UpdateSchedule> activeSchedules = scheduleRepository.findByStatusIn(
                                List.of(ScheduleStatus.APPROVED, ScheduleStatus.IN_PROGRESS));

                List<DashboardSummaryResponse.ActiveRollout> activeRollouts = activeSchedules.stream()
                                .map(schedule -> {
                                        long total = deviceUpdateRepository.countByScheduleId(schedule.getId());
                                        long completed = deviceUpdateRepository.countByScheduleIdAndCurrentState(
                                                        schedule.getId(), UpdateState.INSTALLATION_COMPLETED);
                                        long failed = deviceUpdateRepository.countByScheduleIdAndCurrentState(
                                                        schedule.getId(), UpdateState.FAILED);
                                        double progress = total > 0 ? Math.round(completed * 1000.0 / total) / 10.0 : 0;
                                        double successRate = total > 0
                                                        ? Math.round(completed * 1000.0 / total) / 10.0
                                                        : 0;
                                        double failureRate = total > 0
                                                        ? Math.round(failed * 1000.0 / total) / 10.0
                                                        : 0;

                                        return DashboardSummaryResponse.ActiveRollout.builder()
                                                        .scheduleId(schedule.getId())
                                                        .targetVersion(String.valueOf(schedule.getToVersionCode()))
                                                        .progress(progress)
                                                        .status(schedule.getStatus().name())
                                                        .successCount(completed)
                                                        .failureCount(failed)
                                                        .successRate(successRate)
                                                        .failureRate(failureRate)
                                                        .build();
                                })
                                .collect(Collectors.toList());

                return DashboardSummaryResponse.builder()
                                .totalDevices(totalDevices)
                                .activeDevices(activeDevices)
                                .inactiveDevices(inactiveDevices)
                                .versionDistribution(versionDistribution)
                                .regionBreakdown(regionBreakdown)
                                .activeRollouts(activeRollouts)
                                .versionHeatmap(versionHeatmap)
                                .build();
        }

        @Transactional(readOnly = true)
        public AuditTimelineResponse getDeviceTimeline(UUID deviceId) {
                List<AuditLog> logs = auditLogRepository.findByActorIdOrderByCreatedAtAsc(deviceId);

                // Also get audit logs where this device is the entity
                List<AuditLog> entityLogs = auditLogRepository.findByEntityIdOrderByCreatedAtAsc(deviceId);

                // Merge and sort
                Set<UUID> seen = new HashSet<>();
                List<AuditLog> allLogs = new ArrayList<>();
                for (AuditLog l : logs) {
                        if (seen.add(l.getId()))
                                allLogs.add(l);
                }
                for (AuditLog l : entityLogs) {
                        if (seen.add(l.getId()))
                                allLogs.add(l);
                }
                allLogs.sort(Comparator.comparing(AuditLog::getCreatedAt));

                List<AuditTimelineResponse.AuditEventResponse> events = allLogs.stream()
                                .map(l -> AuditTimelineResponse.AuditEventResponse.builder()
                                                .timestamp(l.getCreatedAt())
                                                .action(l.getAction())
                                                .fromState(l.getFromState())
                                                .toState(l.getToState())
                                                .detail(buildDetail(l))
                                                .actor(l.getActorId() != null ? l.getActorId().toString() : "SYSTEM")
                                                .actorType(l.getActorType() != null ? l.getActorType().name()
                                                                : "SYSTEM")
                                                .build())
                                .collect(Collectors.toList());

                return AuditTimelineResponse.builder()
                                .entityId(deviceId)
                                .entityType("DEVICE")
                                .timeline(events)
                                .build();
        }

        @Transactional(readOnly = true)
        public AuditTimelineResponse getScheduleAudit(UUID scheduleId) {
                List<AuditLog> logs = auditLogRepository.findByEntityIdOrderByCreatedAtAsc(scheduleId);

                List<AuditTimelineResponse.AuditEventResponse> events = logs.stream()
                                .map(l -> AuditTimelineResponse.AuditEventResponse.builder()
                                                .timestamp(l.getCreatedAt())
                                                .action(l.getAction())
                                                .fromState(l.getFromState())
                                                .toState(l.getToState())
                                                .detail(buildDetail(l))
                                                .actor(l.getActorId() != null ? l.getActorId().toString() : "SYSTEM")
                                                .actorType(l.getActorType() != null ? l.getActorType().name()
                                                                : "SYSTEM")
                                                .build())
                                .collect(Collectors.toList());

                return AuditTimelineResponse.builder()
                                .entityId(scheduleId)
                                .entityType("SCHEDULE")
                                .timeline(events)
                                .build();
        }

        private String buildDetail(AuditLog log) {
                Map<String, Object> meta = log.getMetadata();
                if (meta == null || meta.isEmpty()) {
                        return log.getAction();
                }
                StringBuilder sb = new StringBuilder(log.getAction());
                meta.forEach((key, value) -> sb.append(" | ").append(key).append(": ").append(value));
                return sb.toString();
        }
}
