package com.moveinsync.mdm.service;

import com.moveinsync.mdm.dto.request.ScheduleUpdateRequest;
import com.moveinsync.mdm.dto.response.CompatibilityCheckResponse;
import com.moveinsync.mdm.dto.response.ScheduleResponse;
import com.moveinsync.mdm.entity.*;
import com.moveinsync.mdm.enums.*;
import com.moveinsync.mdm.exception.DowngradeNotAllowedException;
import com.moveinsync.mdm.exception.NoUpgradePathException;
import com.moveinsync.mdm.exception.ScheduleNotFoundException;
import com.moveinsync.mdm.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UpdateScheduleService {

    private static final Logger log = LoggerFactory.getLogger(UpdateScheduleService.class);
    private final UpdateScheduleRepository scheduleRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceUpdateRepository deviceUpdateRepository;
    private final AppVersionRepository appVersionRepository;
    private final AdminRepository adminRepository;
    private final VersionCompatibilityService compatibilityService;
    private final AuditService auditService;

    @Transactional
    public ScheduleResponse scheduleUpdate(ScheduleUpdateRequest request, UUID adminId) {
        // 1. DOWNGRADE PREVENTION — the most critical validation
        if (request.getToVersionCode() <= request.getFromVersionCode()) {
            throw new DowngradeNotAllowedException(
                    "Cannot schedule update from version " + request.getFromVersionCode() +
                            " to version " + request.getToVersionCode() + ". Downgrade is strictly prohibited.");
        }

        // 2. Check compatibility path exists
        CompatibilityCheckResponse compatibility = compatibilityService.checkUpgradePath(
                request.getFromVersionCode(), request.getToVersionCode());
        if (!compatibility.isAllowed()) {
            throw new NoUpgradePathException(
                    "No valid upgrade path defined from version " + request.getFromVersionCode() +
                            " to " + request.getToVersionCode() + ". Define compatibility rules first.");
        }

        // 3. Find target version's version name for device matching
        AppVersion fromVersion = appVersionRepository.findByVersionCode(request.getFromVersionCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Source version with code " + request.getFromVersionCode() + " not found."));

        // 4. Find target devices
        List<Device> targetDevices = deviceRepository.findTargetDevices(
                fromVersion.getVersionName(),
                request.getTargetRegion(),
                request.getTargetClientTag());

        if (targetDevices.isEmpty()) {
            throw new IllegalArgumentException(
                    "No active devices found matching the target criteria (version: " +
                            fromVersion.getVersionName() + ", region: " + request.getTargetRegion() + ").");
        }

        // 5. Validate rollout type
        RolloutType rolloutType;
        try {
            rolloutType = RolloutType.valueOf(request.getRolloutType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid rollout type: " + request.getRolloutType() +
                    ". Must be IMMEDIATE, SCHEDULED, or PHASED.");
        }

        // 6. Get admin entity
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found."));

        // 7. Create the schedule
        UpdateSchedule schedule = UpdateSchedule.builder()
                .createdBy(admin)
                .fromVersionCode(request.getFromVersionCode())
                .toVersionCode(request.getToVersionCode())
                .targetRegion(request.getTargetRegion())
                .targetClientTag(request.getTargetClientTag())
                .rolloutType(rolloutType)
                .rolloutPercentage(request.getRolloutPercentage() != null ? request.getRolloutPercentage() : 100)
                .scheduledAt(request.getScheduledAt())
                .status(ScheduleStatus.PENDING_APPROVAL)
                .build();

        schedule = scheduleRepository.save(schedule);

        // 8. Calculate batch size for phased rollouts
        int batchSize = targetDevices.size();
        if (rolloutType == RolloutType.PHASED && request.getRolloutPercentage() != null) {
            batchSize = (int) Math.ceil(targetDevices.size() * request.getRolloutPercentage() / 100.0);
        }

        // 9. Create DeviceUpdate records for target devices (or batch for phased)
        List<Device> selectedDevices = targetDevices.subList(0, Math.min(batchSize, targetDevices.size()));
        for (Device device : selectedDevices) {
            DeviceUpdate deviceUpdate = DeviceUpdate.builder()
                    .schedule(schedule)
                    .device(device)
                    .currentState(UpdateState.SCHEDULED)
                    .build();
            deviceUpdateRepository.save(deviceUpdate);
        }

        // 10. Audit log
        auditService.logAction(AuditEntityType.SCHEDULE, schedule.getId(),
                "SCHEDULE_CREATED", adminId, ActorType.ADMIN,
                Map.of("fromVersion", request.getFromVersionCode(),
                        "toVersion", request.getToVersionCode(),
                        "targetRegion", request.getTargetRegion() != null ? request.getTargetRegion() : "ALL",
                        "targetDeviceCount", targetDevices.size(),
                        "batchSize", batchSize));

        log.info("Update scheduled: {} → {}, {} devices targeted, batch size {}",
                request.getFromVersionCode(), request.getToVersionCode(),
                targetDevices.size(), batchSize);

        return ScheduleResponse.builder()
                .scheduleId(schedule.getId())
                .status(schedule.getStatus().name())
                .fromVersionCode(schedule.getFromVersionCode())
                .toVersionCode(schedule.getToVersionCode())
                .targetRegion(schedule.getTargetRegion())
                .targetClientTag(schedule.getTargetClientTag())
                .rolloutType(schedule.getRolloutType().name())
                .rolloutPercentage(schedule.getRolloutPercentage())
                .targetDeviceCount((long) targetDevices.size())
                .initialBatchSize((long) batchSize)
                .scheduledAt(schedule.getScheduledAt())
                .createdAt(schedule.getCreatedAt())
                .message("Update scheduled. Awaiting approval from Product Head before execution.")
                .build();
    }

    @Transactional
    public ScheduleResponse approveSchedule(UUID scheduleId, UUID adminId) {
        UpdateSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ScheduleNotFoundException("Schedule not found: " + scheduleId));

        if (schedule.getStatus() != ScheduleStatus.PENDING_APPROVAL) {
            throw new IllegalArgumentException(
                    "Schedule is not in PENDING_APPROVAL status. Current: " + schedule.getStatus());
        }

        Admin approver = adminRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found."));

        schedule.setStatus(ScheduleStatus.APPROVED);
        schedule.setApprovedBy(approver);
        schedule.setApprovedAt(LocalDateTime.now());
        scheduleRepository.save(schedule);

        // Mark all device updates as NOTIFIED (simulating push notification)
        List<DeviceUpdate> deviceUpdates = deviceUpdateRepository.findByScheduleId(scheduleId);
        for (DeviceUpdate du : deviceUpdates) {
            du.setCurrentState(UpdateState.NOTIFIED);
            deviceUpdateRepository.save(du);
        }

        auditService.logEvent(AuditEntityType.SCHEDULE, schedule.getId(),
                "SCHEDULE_APPROVED", "PENDING_APPROVAL", "APPROVED",
                adminId, ActorType.ADMIN, Map.of("approvedBy", approver.getUsername()));

        log.info("Schedule {} approved by admin {}", scheduleId, adminId);

        return ScheduleResponse.builder()
                .scheduleId(schedule.getId())
                .status(schedule.getStatus().name())
                .fromVersionCode(schedule.getFromVersionCode())
                .toVersionCode(schedule.getToVersionCode())
                .approvedBy(approver.getUsername())
                .approvedAt(schedule.getApprovedAt())
                .message("Schedule approved. Devices have been notified.")
                .build();
    }

    @Transactional
    public ScheduleResponse rejectSchedule(UUID scheduleId, UUID adminId, String reason) {
        UpdateSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ScheduleNotFoundException("Schedule not found: " + scheduleId));

        if (schedule.getStatus() != ScheduleStatus.PENDING_APPROVAL) {
            throw new IllegalArgumentException("Schedule is not in PENDING_APPROVAL status.");
        }

        schedule.setStatus(ScheduleStatus.REJECTED);
        scheduleRepository.save(schedule);

        auditService.logEvent(AuditEntityType.SCHEDULE, schedule.getId(),
                "SCHEDULE_REJECTED", "PENDING_APPROVAL", "REJECTED",
                adminId, ActorType.ADMIN,
                Map.of("reason", reason != null ? reason : "No reason provided"));

        log.info("Schedule {} rejected by admin {}. Reason: {}", scheduleId, adminId, reason);

        return ScheduleResponse.builder()
                .scheduleId(schedule.getId())
                .status(schedule.getStatus().name())
                .message("Schedule rejected. Reason: " + (reason != null ? reason : "No reason provided"))
                .build();
    }

    @Transactional(readOnly = true)
    public ScheduleResponse getSchedule(UUID scheduleId) {
        UpdateSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ScheduleNotFoundException("Schedule not found: " + scheduleId));

        long totalDevices = deviceUpdateRepository.countByScheduleId(scheduleId);
        String approvedByUsername = schedule.getApprovedBy() != null ? schedule.getApprovedBy().getUsername() : null;

        return ScheduleResponse.builder()
                .scheduleId(schedule.getId())
                .status(schedule.getStatus().name())
                .fromVersionCode(schedule.getFromVersionCode())
                .toVersionCode(schedule.getToVersionCode())
                .targetRegion(schedule.getTargetRegion())
                .targetClientTag(schedule.getTargetClientTag())
                .rolloutType(schedule.getRolloutType().name())
                .rolloutPercentage(schedule.getRolloutPercentage())
                .targetDeviceCount(totalDevices)
                .scheduledAt(schedule.getScheduledAt())
                .approvedBy(approvedByUsername)
                .approvedAt(schedule.getApprovedAt())
                .createdAt(schedule.getCreatedAt())
                .build();
    }
}
