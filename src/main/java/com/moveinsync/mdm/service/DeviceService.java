package com.moveinsync.mdm.service;

import com.moveinsync.mdm.dto.request.DeviceRegisterRequest;
import com.moveinsync.mdm.dto.request.HeartbeatRequest;
import com.moveinsync.mdm.dto.response.DeviceResponse;
import com.moveinsync.mdm.dto.response.HeartbeatResponse;
import com.moveinsync.mdm.entity.AppVersion;
import com.moveinsync.mdm.entity.Device;
import com.moveinsync.mdm.entity.DeviceUpdate;
import com.moveinsync.mdm.enums.*;
import com.moveinsync.mdm.exception.DeviceAlreadyExistsException;
import com.moveinsync.mdm.exception.DeviceNotFoundException;
import com.moveinsync.mdm.repository.AppVersionRepository;
import com.moveinsync.mdm.repository.DeviceRepository;
import com.moveinsync.mdm.repository.DeviceUpdateRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeviceService {

        private static final Logger log = LoggerFactory.getLogger(DeviceService.class);
        private final DeviceRepository deviceRepository;
        private final DeviceUpdateRepository deviceUpdateRepository;
        private final AppVersionRepository appVersionRepository;
        private final AuditService auditService;
        private final HeartbeatBufferService heartbeatBufferService;
        private final MeterRegistry meterRegistry;

        @Transactional
        public DeviceResponse registerDevice(DeviceRegisterRequest request) {
                if (deviceRepository.existsByImei(request.getImei())) {
                        throw new DeviceAlreadyExistsException(
                                        "Device with IMEI " + request.getImei() + " is already registered.");
                }

                Device device = Device.builder()
                                .imei(request.getImei())
                                .appVersion(request.getAppVersion())
                                .deviceOs(request.getDeviceOs())
                                .deviceModel(request.getDeviceModel())
                                .region(request.getRegion())
                                .clientTag(request.getClientTag())
                                .status(DeviceStatus.ACTIVE)
                                .build();

                device = deviceRepository.save(device);

                // Prometheus: track device registrations
                meterRegistry.counter("mdm.device.registered.total",
                                "region", device.getRegion() != null ? device.getRegion() : "unknown").increment();

                auditService.logAction(AuditEntityType.DEVICE, device.getId(),
                                "DEVICE_REGISTERED", device.getId(), ActorType.DEVICE,
                                Map.of("imei", request.getImei(), "appVersion", request.getAppVersion()));

                log.info("Device registered: IMEI={}, region={}, version={}",
                                request.getImei(), request.getRegion(), request.getAppVersion());

                return DeviceResponse.builder()
                                .deviceId(device.getId())
                                .imei(device.getImei())
                                .appVersion(device.getAppVersion())
                                .deviceOs(device.getDeviceOs())
                                .deviceModel(device.getDeviceModel())
                                .region(device.getRegion())
                                .clientTag(device.getClientTag())
                                .lastHeartbeat(device.getLastHeartbeat())
                                .status(device.getStatus().name())
                                .registeredAt(device.getCreatedAt())
                                .message("Device registered successfully.")
                                .build();
        }

        @Transactional(readOnly = true)
        public HeartbeatResponse processHeartbeat(HeartbeatRequest request) {
                Device device = deviceRepository.findByImei(request.getImei())
                                .orElseThrow(() -> new DeviceNotFoundException(
                                                "No device found with IMEI " + request.getImei()
                                                                + ". Please register first."));

                // REDIS HEARTBEAT BUFFER: Instead of writing directly to PostgreSQL
                // (which costs a DB connection per heartbeat), we buffer in Redis
                // (sub-millisecond, no connection pool contention).
                //
                // HeartbeatFlushJob flushes Redis → PostgreSQL every 30 seconds.
                //
                // Why Redis (not Kafka) for heartbeats:
                // • Heartbeats are idempotent — losing a few is harmless
                // • Redis write is O(1), sub-millisecond
                // • At 1M devices: 3,333 writes/sec → Redis handles easily,
                // PostgreSQL connection pool (20) would be overwhelmed
                LocalDateTime now = LocalDateTime.now();
                heartbeatBufferService.bufferHeartbeat(
                                request.getImei(), now,
                                request.getRegion(), request.getAppVersion());

                // Prometheus: track heartbeats by region
                meterRegistry.counter("mdm.heartbeat.total",
                                "region", device.getRegion() != null ? device.getRegion() : "unknown").increment();

                // VERSION COMPLIANCE VALIDATION
                // These are READ queries — cheap on PostgreSQL, no need to buffer.
                boolean versionCompliant = true;
                String complianceMessage = null;
                AppVersion reportedVersion = appVersionRepository.findByVersionName(request.getAppVersion())
                                .orElse(null);

                if (reportedVersion != null) {
                        AppVersion latestVersion = appVersionRepository.findTopByIsActiveTrueOrderByVersionCodeDesc()
                                        .orElse(null);
                        if (latestVersion != null
                                        && reportedVersion.getVersionCode() < latestVersion.getVersionCode()) {
                                versionCompliant = false;
                                complianceMessage = "Device is on version " + request.getAppVersion()
                                                + " (code " + reportedVersion.getVersionCode()
                                                + "), latest available is " + latestVersion.getVersionName()
                                                + " (code " + latestVersion.getVersionCode() + ")";
                                log.info("Version compliance check: IMEI={} — {}", request.getImei(),
                                                complianceMessage);
                        }
                } else {
                        log.warn("Heartbeat from IMEI={} reports unknown version '{}'. Not updating device version.",
                                        request.getImei(), request.getAppVersion());
                        versionCompliant = false;
                        complianceMessage = "Reported version '" + request.getAppVersion()
                                        + "' is not recognized. Device version not updated.";
                }

                // Check for pending updates (READ query — cheap)
                HeartbeatResponse.PendingUpdateInfo pendingUpdate = null;
                List<DeviceUpdate> scheduledUpdates = deviceUpdateRepository.findScheduledByDeviceId(device.getId());

                if (!scheduledUpdates.isEmpty()) {
                        DeviceUpdate latestUpdate = scheduledUpdates.get(0);
                        AppVersion targetVersion = appVersionRepository
                                        .findByVersionCode(latestUpdate.getSchedule().getToVersionCode())
                                        .orElse(null);

                        if (targetVersion != null) {
                                pendingUpdate = HeartbeatResponse.PendingUpdateInfo.builder()
                                                .updateId(latestUpdate.getId().toString())
                                                .targetVersion(targetVersion.getVersionName())
                                                .mandatory(targetVersion.getIsMandatory())
                                                .build();
                        }
                }

                return HeartbeatResponse.builder()
                                .acknowledged(true)
                                .lastHeartbeat(now)
                                .pendingUpdate(pendingUpdate)
                                .versionCompliant(versionCompliant)
                                .complianceMessage(complianceMessage)
                                .build();
        }

        @Transactional(readOnly = true)
        public Page<DeviceResponse> listDevices(String region, String appVersion, String clientTag,
                        DeviceStatus status, Pageable pageable) {
                Page<Device> devices = deviceRepository.findWithFilters(region, appVersion, clientTag, status,
                                pageable);

                return devices.map(d -> DeviceResponse.builder()
                                .deviceId(d.getId())
                                .imei(d.getImei())
                                .appVersion(d.getAppVersion())
                                .deviceOs(d.getDeviceOs())
                                .deviceModel(d.getDeviceModel())
                                .region(d.getRegion())
                                .clientTag(d.getClientTag())
                                .lastHeartbeat(d.getLastHeartbeat())
                                .status(d.getStatus().name())
                                .registeredAt(d.getCreatedAt())
                                .build());
        }

        @Transactional(readOnly = true)
        public DeviceResponse getDeviceById(UUID deviceId) {
                Device device = deviceRepository.findById(deviceId)
                                .orElseThrow(() -> new DeviceNotFoundException(
                                                "Device not found with ID: " + deviceId));

                return DeviceResponse.builder()
                                .deviceId(device.getId())
                                .imei(device.getImei())
                                .appVersion(device.getAppVersion())
                                .deviceOs(device.getDeviceOs())
                                .deviceModel(device.getDeviceModel())
                                .region(device.getRegion())
                                .clientTag(device.getClientTag())
                                .lastHeartbeat(device.getLastHeartbeat())
                                .status(device.getStatus().name())
                                .registeredAt(device.getCreatedAt())
                                .build();
        }
}
