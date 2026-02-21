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

    @Transactional
    public HeartbeatResponse processHeartbeat(HeartbeatRequest request) {
        Device device = deviceRepository.findByImei(request.getImei())
                .orElseThrow(() -> new DeviceNotFoundException(
                        "No device found with IMEI " + request.getImei() + ". Please register first."));

        // Update device metadata
        device.setLastHeartbeat(LocalDateTime.now());
        device.setAppVersion(request.getAppVersion());
        if (request.getRegion() != null) {
            device.setRegion(request.getRegion());
        }
        device.setStatus(DeviceStatus.ACTIVE);
        deviceRepository.save(device);

        // Check for pending updates
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
                .lastHeartbeat(device.getLastHeartbeat())
                .pendingUpdate(pendingUpdate)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<DeviceResponse> listDevices(String region, String appVersion, String clientTag,
            DeviceStatus status, Pageable pageable) {
        Page<Device> devices = deviceRepository.findWithFilters(region, appVersion, clientTag, status, pageable);

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
                .orElseThrow(() -> new DeviceNotFoundException("Device not found with ID: " + deviceId));

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
