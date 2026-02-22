package com.moveinsync.mdm.controller;

import com.moveinsync.mdm.dto.request.RejectScheduleRequest;
import com.moveinsync.mdm.dto.request.ScheduleUpdateRequest;
import com.moveinsync.mdm.dto.request.UpdateStatusRequest;
import com.moveinsync.mdm.dto.response.DeviceUpdateResponse;
import com.moveinsync.mdm.dto.response.ScheduleResponse;
import com.moveinsync.mdm.security.AdminIdResolver;
import com.moveinsync.mdm.service.DeviceUpdateService;
import com.moveinsync.mdm.service.UpdateScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/updates")
@RequiredArgsConstructor
@Tag(name = "Updates", description = "Update scheduling, approval, and device update status endpoints")
public class UpdateController {

    private final UpdateScheduleService updateScheduleService;
    private final DeviceUpdateService deviceUpdateService;
    private final AdminIdResolver adminIdResolver;

    // ==================== SCHEDULE MANAGEMENT ====================

    @PostMapping("/schedule")
    @Operation(summary = "Schedule Update", description = "Create an update schedule with downgrade prevention and compatibility path validation.")
    public ResponseEntity<ScheduleResponse> scheduleUpdate(
            @Valid @RequestBody ScheduleUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID adminId = adminIdResolver.resolve(userDetails);
        return ResponseEntity.status(HttpStatus.CREATED).body(updateScheduleService.scheduleUpdate(request, adminId));
    }

    @PutMapping("/schedule/{scheduleId}/approve")
    @Operation(summary = "Approve Schedule", description = "Approve a pending update schedule. SUPER_ADMIN only.")
    public ResponseEntity<ScheduleResponse> approveSchedule(
            @PathVariable UUID scheduleId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID adminId = adminIdResolver.resolve(userDetails);
        return ResponseEntity.ok(updateScheduleService.approveSchedule(scheduleId, adminId));
    }

    @PutMapping("/schedule/{scheduleId}/reject")
    @Operation(summary = "Reject Schedule", description = "Reject a pending update schedule with optional reason. SUPER_ADMIN only.")
    public ResponseEntity<ScheduleResponse> rejectSchedule(
            @PathVariable UUID scheduleId,
            @RequestBody(required = false) RejectScheduleRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID adminId = adminIdResolver.resolve(userDetails);
        String reason = request != null ? request.getReason() : null;
        return ResponseEntity.ok(updateScheduleService.rejectSchedule(scheduleId, adminId, reason));
    }

    @GetMapping("/schedule/{scheduleId}")
    @Operation(summary = "Get Schedule", description = "Get update schedule details with progress information.")
    public ResponseEntity<ScheduleResponse> getSchedule(@PathVariable UUID scheduleId) {
        return ResponseEntity.ok(updateScheduleService.getSchedule(scheduleId));
    }

    // ==================== DEVICE UPDATE STATUS ====================

    @PutMapping("/{deviceUpdateId}/status")
    @Operation(summary = "Report Update Status", description = "Device reports its update progress. Public endpoint. State machine validation enforced.")
    public ResponseEntity<DeviceUpdateResponse> reportUpdateStatus(
            @PathVariable UUID deviceUpdateId,
            @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(deviceUpdateService.updateStatus(deviceUpdateId, request));
    }
}
