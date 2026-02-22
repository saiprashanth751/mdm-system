package com.moveinsync.mdm.service;

import com.moveinsync.mdm.dto.request.UpdateStatusRequest;
import com.moveinsync.mdm.dto.response.DeviceUpdateResponse;
import com.moveinsync.mdm.entity.AppVersion;
import com.moveinsync.mdm.entity.Device;
import com.moveinsync.mdm.entity.DeviceUpdate;
import com.moveinsync.mdm.entity.UpdateSchedule;
import com.moveinsync.mdm.enums.*;
import com.moveinsync.mdm.exception.DowngradeNotAllowedException;
import com.moveinsync.mdm.exception.InvalidStateTransitionException;
import com.moveinsync.mdm.repository.AppVersionRepository;
import com.moveinsync.mdm.repository.DeviceRepository;
import com.moveinsync.mdm.repository.DeviceUpdateRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Gap #6: Unit tests for DeviceUpdateService — state transitions and downgrade
 * prevention.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceUpdateService — State Transition & Downgrade Tests")
class DeviceUpdateServiceTest {

        @Mock
        private DeviceUpdateRepository deviceUpdateRepository;

        @Mock
        private DeviceRepository deviceRepository;

        @Mock
        private AppVersionRepository appVersionRepository;

        @Mock
        private AuditService auditService;

        // Use real in-memory registry — no stubbing needed, avoids
        // UnnecessaryStubbingException
        private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

        private DeviceUpdateService service;

        @BeforeEach
        void setUp() {
                service = new DeviceUpdateService(deviceUpdateRepository, deviceRepository,
                                appVersionRepository, auditService, meterRegistry);
        }

        private DeviceUpdate createDeviceUpdate(UpdateState state, int toVersionCode) {
                UUID duId = UUID.randomUUID();

                UpdateSchedule schedule = UpdateSchedule.builder()
                                .id(UUID.randomUUID())
                                .toVersionCode(toVersionCode)
                                .fromVersionCode(100)
                                .build();

                Device device = Device.builder()
                                .id(UUID.randomUUID())
                                .appVersion("1.0.0")
                                .imei("123456789012345")
                                .build();

                DeviceUpdate du = DeviceUpdate.builder()
                                .id(duId)
                                .currentState(state)
                                .retryCount(0)
                                .build();
                du.setSchedule(schedule);
                du.setDevice(device);

                return du;
        }

        // ==================== VALID STATE TRANSITIONS ====================

        @Nested
        @DisplayName("Valid state transitions")
        class ValidTransitions {

                @Test
                @DisplayName("NOTIFIED → DOWNLOAD_STARTED succeeds")
                void notifiedToDownloadStarted() {
                        DeviceUpdate du = createDeviceUpdate(UpdateState.NOTIFIED, 110);

                        when(deviceUpdateRepository.findById(du.getId())).thenReturn(Optional.of(du));
                        when(deviceUpdateRepository.save(any(DeviceUpdate.class))).thenAnswer(i -> i.getArgument(0));

                        UpdateStatusRequest request = new UpdateStatusRequest();
                        request.setImei("123456789012345");
                        request.setNewState("DOWNLOAD_STARTED");

                        DeviceUpdateResponse response = service.updateStatus(du.getId(), request);

                        assertEquals("DOWNLOAD_STARTED", response.getCurrentState());
                        verify(deviceUpdateRepository).save(any(DeviceUpdate.class));
                }
        }

        // ==================== INVALID STATE TRANSITIONS ====================

        @Nested
        @DisplayName("Invalid state transitions")
        class InvalidTransitions {

                @Test
                @DisplayName("NOTIFIED → DOWNLOAD_COMPLETED should throw InvalidStateTransitionException")
                void skipStatesBlocked() {
                        DeviceUpdate du = createDeviceUpdate(UpdateState.NOTIFIED, 110);

                        when(deviceUpdateRepository.findById(du.getId())).thenReturn(Optional.of(du));

                        UpdateStatusRequest request = new UpdateStatusRequest();
                        request.setImei("123456789012345");
                        request.setNewState("DOWNLOAD_COMPLETED");

                        assertThrows(InvalidStateTransitionException.class,
                                        () -> service.updateStatus(du.getId(), request),
                                        "Skipping from NOTIFIED → DOWNLOAD_COMPLETED should throw");
                }

                @Test
                @DisplayName("INSTALLATION_COMPLETED → any state should throw (terminal)")
                void terminalStateBlocked() {
                        DeviceUpdate du = createDeviceUpdate(UpdateState.INSTALLATION_COMPLETED, 110);
                        du.getDevice().setAppVersion("1.1.0");

                        when(deviceUpdateRepository.findById(du.getId())).thenReturn(Optional.of(du));

                        UpdateStatusRequest request = new UpdateStatusRequest();
                        request.setImei("123456789012345");
                        request.setNewState("DOWNLOAD_STARTED");

                        assertThrows(InvalidStateTransitionException.class,
                                        () -> service.updateStatus(du.getId(), request),
                                        "Transition from terminal INSTALLATION_COMPLETED should throw");
                }
        }

        // ==================== DOWNGRADE PREVENTION ====================

        @Nested
        @DisplayName("Downgrade prevention (Gap #15)")
        class DowngradePrevention {

                @Test
                @DisplayName("Downgrade should throw DowngradeNotAllowedException")
                void downgradeBlocked() {
                        // Device is on version 1.1.0 (code 110), schedule target is code 90 →
                        // downgrade!
                        DeviceUpdate du = createDeviceUpdate(UpdateState.DOWNLOAD_COMPLETED, 90);
                        du.getDevice().setAppVersion("1.1.0");

                        AppVersion currentVersion = AppVersion.builder()
                                        .versionCode(110)
                                        .versionName("1.1.0")
                                        .build();

                        when(deviceUpdateRepository.findById(du.getId())).thenReturn(Optional.of(du));
                        when(appVersionRepository.findByVersionName("1.1.0")).thenReturn(Optional.of(currentVersion));

                        UpdateStatusRequest request = new UpdateStatusRequest();
                        request.setImei("123456789012345");
                        request.setNewState("INSTALLATION_STARTED");

                        assertThrows(DowngradeNotAllowedException.class,
                                        () -> service.updateStatus(du.getId(), request),
                                        "Installing version 90 when device has 110 should throw");
                }
        }

        // ==================== 404 CASES ====================

        @Nested
        @DisplayName("Not found scenarios")
        class NotFoundScenarios {

                @Test
                @DisplayName("Non-existent device update ID should throw")
                void deviceUpdateNotFound() {
                        UUID duId = UUID.randomUUID();
                        when(deviceUpdateRepository.findById(duId)).thenReturn(Optional.empty());

                        UpdateStatusRequest request = new UpdateStatusRequest();
                        request.setImei("123456789012345");
                        request.setNewState("DOWNLOAD_STARTED");

                        assertThrows(Exception.class,
                                        () -> service.updateStatus(duId, request));
                }
        }
}
