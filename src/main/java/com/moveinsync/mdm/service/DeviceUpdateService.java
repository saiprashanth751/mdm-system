package com.moveinsync.mdm.service;

import com.moveinsync.mdm.dto.request.UpdateStatusRequest;
import com.moveinsync.mdm.dto.response.DeviceUpdateResponse;
import com.moveinsync.mdm.entity.AppVersion;
import com.moveinsync.mdm.entity.Device;
import com.moveinsync.mdm.entity.DeviceUpdate;
import com.moveinsync.mdm.enums.*;
import com.moveinsync.mdm.exception.DowngradeNotAllowedException;
import com.moveinsync.mdm.exception.InvalidStateTransitionException;
import com.moveinsync.mdm.repository.AppVersionRepository;
import com.moveinsync.mdm.repository.DeviceRepository;
import com.moveinsync.mdm.repository.DeviceUpdateRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeviceUpdateService {

        private static final Logger log = LoggerFactory.getLogger(DeviceUpdateService.class);

        private final DeviceUpdateRepository deviceUpdateRepository;
        private final DeviceRepository deviceRepository;
        private final AppVersionRepository appVersionRepository;
        private final AuditService auditService;
        private final MeterRegistry meterRegistry;

        @Transactional
        public DeviceUpdateResponse updateStatus(UUID deviceUpdateId, UpdateStatusRequest request) {
                DeviceUpdate deviceUpdate = deviceUpdateRepository.findById(deviceUpdateId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Device update not found: " + deviceUpdateId));

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
                // Before installation begins, verify the target version is not lower than the
                // device's current version.
                // Uses two indexed queries: one by version name (device's current), one by
                // version code (target).
                if (newState == UpdateState.INSTALLATION_STARTED || newState == UpdateState.INSTALLATION_COMPLETED) {
                        Integer targetVersionCode = deviceUpdate.getSchedule().getToVersionCode();

                        // Gap #15 FIX: Don't silently bypass downgrade check when version name doesn't
                        // match.
                        // Use orElse with explicit handling instead of ifPresent.
                        Optional<AppVersion> currentVersionOpt = appVersionRepository
                                        .findByVersionName(device.getAppVersion());
                        if (currentVersionOpt.isPresent()) {
                                AppVersion currentVersion = currentVersionOpt.get();
                                if (currentVersion.getVersionCode() > targetVersionCode) {
                                        String targetName = appVersionRepository.findByVersionCode(targetVersionCode)
                                                        .map(tv -> tv.getVersionName())
                                                        .orElse(String.valueOf(targetVersionCode));
                                        meterRegistry.counter("mdm.downgrade.blocked.total",
                                                        "reason", "version_downgrade").increment();
                                        throw new DowngradeNotAllowedException(
                                                        "Device is currently on version " + device.getAppVersion() +
                                                                        " (code " + currentVersion.getVersionCode() +
                                                                        "). Installing version " + targetName +
                                                                        " (code " + targetVersionCode
                                                                        + ") is not permitted.");
                                }
                        } else {
                                // Version name not found — log warning and block as a safety measure
                                log.warn("Cannot resolve device version '{}' for downgrade check on device {}. Blocking as precaution.",
                                                device.getAppVersion(), device.getImei());
                                meterRegistry.counter("mdm.downgrade.blocked.total",
                                                "reason", "unresolved_version").increment();
                                throw new IllegalArgumentException(
                                                "Cannot verify downgrade safety: device version '"
                                                                + device.getAppVersion() +
                                                                "' is not recognized in the version catalog.");
                        }
                }

                // STATE MACHINE VALIDATION
                if (!previousState.canTransitionTo(newState)) {
                        throw new InvalidStateTransitionException(
                                        "Cannot transition from " + previousState + " to " + newState +
                                                        ". Allowed transitions from " + previousState + " are limited. "
                                                        +
                                                        "Ensure you follow the correct update lifecycle order.");
                }

                // Handle FAILED state
                if (newState == UpdateState.FAILED) {
                        deviceUpdate.setFailureStage(previousState.name());
                        deviceUpdate.setFailureReason(
                                        request.getFailureReason() != null ? request.getFailureReason()
                                                        : "Unknown failure");
                        deviceUpdate.setRetryCount(deviceUpdate.getRetryCount() + 1);

                        // Prometheus: track update failures by stage
                        meterRegistry.counter("mdm.update.failure.total",
                                        "stage", previousState.name()).increment();

                        log.warn("Device update FAILED: deviceUpdateId={}, stage={}, reason={}, retryCount={}",
                                        deviceUpdateId, previousState, request.getFailureReason(),
                                        deviceUpdate.getRetryCount());
                }

                // Handle successful installation — update device's current version
                if (newState == UpdateState.INSTALLATION_COMPLETED) {
                        appVersionRepository.findByVersionCode(deviceUpdate.getSchedule().getToVersionCode())
                                        .ifPresent(targetVersion -> {
                                                device.setAppVersion(targetVersion.getVersionName());
                                                deviceRepository.save(device);
                                        });

                        // Prometheus: track successful updates
                        meterRegistry.counter("mdm.update.success.total").increment();

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
                                                "failureReason",
                                                request.getFailureReason() != null ? request.getFailureReason()
                                                                : "N/A"));

                return DeviceUpdateResponse.builder()
                                .deviceUpdateId(deviceUpdate.getId())
                                .previousState(previousState.name())
                                .currentState(newState.name())
                                .updatedAt(deviceUpdate.getUpdatedAt())
                                .message("State updated from " + previousState + " to " + newState + ".")
                                .build();
        }
}
