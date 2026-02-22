package com.moveinsync.mdm.service;

import com.moveinsync.mdm.dto.request.DeviceRegisterRequest;
import com.moveinsync.mdm.dto.request.HeartbeatRequest;
import com.moveinsync.mdm.dto.response.DeviceResponse;
import com.moveinsync.mdm.dto.response.HeartbeatResponse;
import com.moveinsync.mdm.entity.AppVersion;
import com.moveinsync.mdm.entity.Device;
import com.moveinsync.mdm.enums.DeviceStatus;
import com.moveinsync.mdm.exception.DeviceAlreadyExistsException;
import com.moveinsync.mdm.exception.DeviceNotFoundException;
import com.moveinsync.mdm.repository.AppVersionRepository;
import com.moveinsync.mdm.repository.DeviceRepository;
import com.moveinsync.mdm.repository.DeviceUpdateRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceService — Registration & Heartbeat Tests")
class DeviceServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private DeviceUpdateRepository deviceUpdateRepository;

    @Mock
    private AppVersionRepository appVersionRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private HeartbeatBufferService heartbeatBufferService;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private DeviceService service;

    @BeforeEach
    void setUp() {
        service = new DeviceService(deviceRepository, deviceUpdateRepository,
                appVersionRepository, auditService, heartbeatBufferService, meterRegistry);
    }

    // ==================== DEVICE REGISTRATION ====================

    @Nested
    @DisplayName("Device Registration")
    class Registration {

        @Test
        @DisplayName("Successful registration returns DeviceResponse with generated ID")
        void registerDevice_success() {
            DeviceRegisterRequest request = new DeviceRegisterRequest();
            request.setImei("123456789012345");
            request.setAppVersion("1.0.0");
            request.setDeviceOs("Android 13");
            request.setDeviceModel("Pixel 7");
            request.setRegion("NORTH");

            when(deviceRepository.existsByImei("123456789012345")).thenReturn(false);
            when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> {
                Device d = invocation.getArgument(0);
                d.setId(UUID.randomUUID());
                return d;
            });

            DeviceResponse response = service.registerDevice(request);

            assertNotNull(response.getDeviceId());
            assertEquals("123456789012345", response.getImei());
            assertEquals("1.0.0", response.getAppVersion());
            assertEquals("ACTIVE", response.getStatus());
            assertNotNull(response.getMessage());
            verify(deviceRepository).save(any(Device.class));
            verify(auditService).logAction(any(), any(), eq("DEVICE_REGISTERED"), any(), any(), any());
        }

        @Test
        @DisplayName("Duplicate IMEI throws DeviceAlreadyExistsException")
        void registerDevice_duplicateImei_throws() {
            DeviceRegisterRequest request = new DeviceRegisterRequest();
            request.setImei("123456789012345");
            request.setAppVersion("1.0.0");

            when(deviceRepository.existsByImei("123456789012345")).thenReturn(true);

            assertThrows(DeviceAlreadyExistsException.class,
                    () -> service.registerDevice(request),
                    "Registering a device with existing IMEI should throw");

            verify(deviceRepository, never()).save(any());
        }
    }

    // ==================== HEARTBEAT PROCESSING ====================

    @Nested
    @DisplayName("Heartbeat Processing (Redis Buffer)")
    class HeartbeatProcessing {

        @Test
        @DisplayName("Valid heartbeat buffers to Redis instead of PostgreSQL")
        void processHeartbeat_buffersToRedis() {
            Device device = Device.builder()
                    .id(UUID.randomUUID())
                    .imei("123456789012345")
                    .appVersion("1.0.0")
                    .region("NORTH")
                    .status(DeviceStatus.ACTIVE)
                    .build();

            AppVersion version = AppVersion.builder()
                    .versionName("1.0.0")
                    .versionCode(100)
                    .isActive(true)
                    .build();

            when(deviceRepository.findByImei("123456789012345")).thenReturn(Optional.of(device));
            when(appVersionRepository.findByVersionName("1.0.0")).thenReturn(Optional.of(version));
            when(appVersionRepository.findTopByIsActiveTrueOrderByVersionCodeDesc()).thenReturn(Optional.of(version));
            when(deviceUpdateRepository.findScheduledByDeviceId(device.getId())).thenReturn(Collections.emptyList());

            HeartbeatRequest request = new HeartbeatRequest();
            request.setImei("123456789012345");
            request.setAppVersion("1.0.0");
            request.setRegion("NORTH");

            HeartbeatResponse response = service.processHeartbeat(request);

            assertTrue(response.isAcknowledged());
            assertTrue(response.isVersionCompliant());
            assertNotNull(response.getLastHeartbeat());

            // Verify heartbeat was buffered in Redis, NOT written to DB
            verify(heartbeatBufferService).bufferHeartbeat(eq("123456789012345"), any(), eq("NORTH"), eq("1.0.0"));
            verify(deviceRepository, never()).save(any(Device.class));
        }

        @Test
        @DisplayName("Unknown IMEI throws DeviceNotFoundException")
        void processHeartbeat_unknownImei_throws() {
            when(deviceRepository.findByImei("999999999999999")).thenReturn(Optional.empty());

            HeartbeatRequest request = new HeartbeatRequest();
            request.setImei("999999999999999");
            request.setAppVersion("1.0.0");

            assertThrows(DeviceNotFoundException.class,
                    () -> service.processHeartbeat(request));
        }

        @Test
        @DisplayName("Non-compliant version is detected and flagged")
        void processHeartbeat_nonCompliantVersion() {
            Device device = Device.builder()
                    .id(UUID.randomUUID())
                    .imei("123456789012345")
                    .appVersion("1.0.0")
                    .region("NORTH")
                    .status(DeviceStatus.ACTIVE)
                    .build();

            AppVersion oldVersion = AppVersion.builder()
                    .versionName("1.0.0")
                    .versionCode(100)
                    .isActive(true)
                    .build();

            AppVersion latestVersion = AppVersion.builder()
                    .versionName("2.0.0")
                    .versionCode(200)
                    .isActive(true)
                    .build();

            when(deviceRepository.findByImei("123456789012345")).thenReturn(Optional.of(device));
            when(appVersionRepository.findByVersionName("1.0.0")).thenReturn(Optional.of(oldVersion));
            when(appVersionRepository.findTopByIsActiveTrueOrderByVersionCodeDesc())
                    .thenReturn(Optional.of(latestVersion));
            when(deviceUpdateRepository.findScheduledByDeviceId(device.getId())).thenReturn(Collections.emptyList());

            HeartbeatRequest request = new HeartbeatRequest();
            request.setImei("123456789012345");
            request.setAppVersion("1.0.0");

            HeartbeatResponse response = service.processHeartbeat(request);

            assertFalse(response.isVersionCompliant());
            assertNotNull(response.getComplianceMessage());
        }
    }
}
