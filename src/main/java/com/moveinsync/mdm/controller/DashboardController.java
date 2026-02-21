package com.moveinsync.mdm.controller;

import com.moveinsync.mdm.dto.response.AuditTimelineResponse;
import com.moveinsync.mdm.dto.response.DashboardSummaryResponse;
import com.moveinsync.mdm.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Dashboard & Audit", description = "System monitoring dashboard and audit trail endpoints")
public class DashboardController {

    private final DashboardService dashboardService;

    // ==================== DASHBOARD ====================

    @GetMapping("/dashboard/summary")
    @Operation(summary = "Dashboard Summary", description = "Real-time system overview: device counts, version distribution, region breakdown, active rollouts.")
    public ResponseEntity<DashboardSummaryResponse> getDashboardSummary() {
        return ResponseEntity.ok(dashboardService.getDashboardSummary());
    }

    // ==================== AUDIT TIMELINES ====================

    @GetMapping("/audit/devices/{deviceId}")
    @Operation(summary = "Device Audit Timeline", description = "Chronological audit trail for a specific device.")
    public ResponseEntity<AuditTimelineResponse> getDeviceTimeline(@PathVariable UUID deviceId) {
        return ResponseEntity.ok(dashboardService.getDeviceTimeline(deviceId));
    }

    @GetMapping("/audit/schedules/{scheduleId}")
    @Operation(summary = "Schedule Audit Trail", description = "Audit trail for a specific update schedule.")
    public ResponseEntity<AuditTimelineResponse> getScheduleAudit(@PathVariable UUID scheduleId) {
        return ResponseEntity.ok(dashboardService.getScheduleAudit(scheduleId));
    }
}
