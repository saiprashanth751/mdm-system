package com.moveinsync.mdm.service;

import com.moveinsync.mdm.dto.request.UpdateStatusRequest;
import com.moveinsync.mdm.dto.response.DeviceUpdateResponse;
import com.moveinsync.mdm.entity.Device;
import com.moveinsync.mdm.entity.DeviceUpdate;
import com.moveinsync.mdm.enums.*;
import com.moveinsync.mdm.exception.DeviceNotFoundException;
import com.moveinsync.mdm.exception.DowngradeNotAllowedException;
import com.moveinsync.mdm.exception.InvalidStateTransitionException;
import com.moveinsync.mdm.repository.AppVersionRepository;
import com.moveinsync.mdm.repository.DeviceRepository;
import com.moveinsync.mdm.repository.DeviceUpdateRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeviceUpdateService {

    private static final Logger log = LoggerFactory.getLogger(DeviceUpdateService.class);
    private static final int MAX_RETRIES = 3;

    private final DeviceUpdateRepository deviceUpdateRepository;
    private final DeviceRepository deviceRepository;
    private final AppVersionRepository appVersionRepository;
    private final AuditService auditService;

    @Transactional
    public DeviceUpdateResponse updateStatus(UUID deviceUpdateId, UpdateStatusRequest request) {
        DeviceUpdate deviceUpdate = deviceUpdateRepository.findById(deviceUpdateId)
                .orElseThrow(() -> new IllegalArgumentException("Device update not found: " + deviceUpdateId));

        Device device = deviceUpdate.getDevice();

        // Validate IMEI matches
        if (!device.getImei().equals(request.getImei())) {
            throw new IllegalArgumentException(
                    "IMEI mismatch. This update does not belong to device: " + request.getImei());
        }

        // Parse new state
        UpdateState newState;
        try {
            newState = UpdateState.valueOf(request.getNewState().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid state: " + request.getNewState() +
                    ". Valid states: SCHEDULED, NOTIFIED, DOWNLOAD_STARTED, DOWNLOAD_COMPLETED, " +
                    "INSTALLATION_STARTED, INSTALLATION_COMPLETED, FAILED");
        }

        UpdateState previousState = deviceUpdate.getCurrentState();

        // DEVICE-LEVEL DOWNGRADE PREVENTION
        // Check if the target version of this update is lower than current device
        // version
        if (newState == UpdateState.INSTALLATION_STARTED || newState == UpdateState.INSTALLATION_COMPLETED) {
            Integer targetVersionCode = deviceUpdate.getSchedule().getToVersionCode();
            appVersionRepository.findByVersionCode(targetVersionCode).ifPresent(targetVersion -> {
                // Compare version names to detect downgrade
                try {
                    // Simple version comparison by version code
                    appVersionRepository.findByVersionCode(targetVersionCode).ifPresent(tv -> {
                        // If device's current version code is higher, block
                        appVersionRepository.findAll().stream()
                                .filter(v -> v.getVersionName().equals(device.getAppVersion()))
                                .findFirst()
                                .ifPresent(currentVersion -> {
                                    if (currentVersion.getVersionCode() > targetVersionCode) {
                                        throw new DowngradeNotAllowedException(
                                                "Device is currently on version " + device.getAppVersion() +
                                                        ". Installing version " + tv.getVersionName()
                                                        + " is not permitted.");
                                    }
                                });
                    });
                } catch (DowngradeNotAllowedException e) {
                    throw e;
                }
            });
        }

        // STATE MACHINE VALIDATION
        if (!previousState.canTransitionTo(newState)) {
            throw new InvalidStateTransitionException(
                    "Cannot transition from " + previousState + " to " + newState +
                            ". Allowed transitions from " + previousState + " are limited. " +
                            "Ensure you follow the correct update lifecycle order.");
        }

        // Handle FAILED state
        if (newState == UpdateState.FAILED) {
            deviceUpdate.setFailureStage(previousState.name());
            deviceUpdate.setFailureReason(
                    request.getFailureReason() != null ? request.getFailureReason() : "Unknown failure");
            deviceUpdate.setRetryCount(deviceUpdate.getRetryCount() + 1);

            log.warn("Device update FAILED: deviceUpdateId={}, stage={}, reason={}, retryCount={}",
                    deviceUpdateId, previousState, request.getFailureReason(), deviceUpdate.getRetryCount());
        }

        // Handle successful installation — update device's current version
        if (newState == UpdateState.INSTALLATION_COMPLETED) {
            appVersionRepository.findByVersionCode(deviceUpdate.getSchedule().getToVersionCode())
                    .ifPresent(targetVersion -> {
                        device.setAppVersion(targetVersion.getVersionName());
                        deviceRepository.save(device);
                    });

            log.info("Device {} successfully updated to version code {}",
                    device.getImei(), deviceUpdate.getSchedule().getToVersionCode());
        }

        // Update state
        deviceUpdate.setCurrentState(newState);
        deviceUpdateRepository.save(deviceUpdate);

        // Audit trail
        auditService.logEvent(AuditEntityType.DEVICE_UPDATE, deviceUpdateId,
                "STATE_CHANGED", previousState.name(), newState.name(),
                device.getId(), ActorType.DEVICE,
                Map.of("imei", device.getImei(),
                        "failureReason", request.getFailureReason() != null ? request.getFailureReason() : "N/A"));

        return DeviceUpdateResponse.builder()
                .deviceUpdateId(deviceUpdate.getId())
                .previousState(previousState.name())
                .currentState(newState.name())
                .updatedAt(deviceUpdate.getUpdatedAt())
                .message("State updated from " + previousState + " to " + newState + ".")
                .build();
    }
}
