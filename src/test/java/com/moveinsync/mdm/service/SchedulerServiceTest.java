package com.moveinsync.mdm.service;

import com.moveinsync.mdm.entity.Device;
import com.moveinsync.mdm.entity.DeviceUpdate;
import com.moveinsync.mdm.entity.UpdateSchedule;
import com.moveinsync.mdm.enums.*;
import com.moveinsync.mdm.repository.DeviceRepository;
import com.moveinsync.mdm.repository.DeviceUpdateRepository;
import com.moveinsync.mdm.repository.UpdateScheduleRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SchedulerService — Scheduled Tasks Tests")
class SchedulerServiceTest {

        @Mock
        private DeviceRepository deviceRepository;

        @Mock
        private DeviceUpdateRepository deviceUpdateRepository;

        @Mock
        private UpdateScheduleRepository scheduleRepository;

        @Mock
        private AuditService auditService;

        private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        private SchedulerService service;

        @BeforeEach
        void setUp() {
                service = new SchedulerService(deviceRepository, deviceUpdateRepository,
                                scheduleRepository, auditService, meterRegistry);
                // Set default values that would come from @Value
                org.springframework.test.util.ReflectionTestUtils.setField(service, "inactiveThresholdDays", 7);
                org.springframework.test.util.ReflectionTestUtils.setField(service, "maxRetryCount", 3);
        }

        // ==================== INACTIVE DEVICE DETECTION ====================

        @Nested
        @DisplayName("Inactive Device Detection")
        class InactiveDeviceDetection {

                @Test
                @DisplayName("Devices with heartbeat older than threshold are marked INACTIVE")
                void detectInactiveDevices_marksOldDevices() {
                        Device staleDevice = Device.builder()
                                        .id(UUID.randomUUID())
                                        .imei("111111111111111")
                                        .lastHeartbeat(LocalDateTime.now().minusDays(10))
                                        .status(DeviceStatus.ACTIVE)
                                        .build();

                        when(deviceRepository.findByLastHeartbeatBeforeAndStatus(
                                        any(LocalDateTime.class), eq(DeviceStatus.ACTIVE)))
                                        .thenReturn(List.of(staleDevice));

                        service.detectInactiveDevices();

                        verify(deviceRepository).save(argThat(device -> device.getStatus() == DeviceStatus.INACTIVE));
                        verify(auditService).logEvent(any(), any(), eq("MARKED_INACTIVE"),
                                        eq("ACTIVE"), eq("INACTIVE"), any(), any(), any());
                }

        @Test
        @DisplayName("No action when all devices are active")
        void detectInactiveDevices_noInactiveDevices() {
            when(deviceRepository.findByLastHeartbeatBeforeAndStatus(
                    any(LocalDateTime.class), eq(DeviceStatus.ACTIVE)))
                    .thenReturn(Collections.emptyList());

            service.detectInactiveDevices();

            verify(deviceRepository, never()).save(any());
        }
        }

        // ==================== AUTO-RETRY FAILED UPDATES ====================

        @Nested
        @DisplayName("Auto-Retry Failed Updates")
        class RetryLogic {

                @Test
                @DisplayName("FAILED updates with retryCount < max are rescheduled to SCHEDULED")
                void retryFailedUpdates_reschedules() {
                        DeviceUpdate failedUpdate = DeviceUpdate.builder()
                                        .id(UUID.randomUUID())
                                        .currentState(UpdateState.FAILED)
                                        .retryCount(1)
                                        .failureStage("DOWNLOAD_STARTED")
                                        .failureReason("Network timeout")
                                        .build();

                        when(deviceUpdateRepository.findByCurrentStateAndRetryCountLessThan(
                                        UpdateState.FAILED, 3))
                                        .thenReturn(List.of(failedUpdate));

                        service.retryFailedUpdates();

                        verify(deviceUpdateRepository).save(argThat(du -> du.getCurrentState() == UpdateState.SCHEDULED
                                        && du.getFailureStage() == null
                                        && du.getFailureReason() == null));
                }

        @Test
        @DisplayName("No retry when no failed updates exist")
        void retryFailedUpdates_nothingToRetry() {
            when(deviceUpdateRepository.findByCurrentStateAndRetryCountLessThan(
                    UpdateState.FAILED, 3))
                    .thenReturn(Collections.emptyList());

            service.retryFailedUpdates();

            verify(deviceUpdateRepository, never()).save(any());
        }
        }

        // ==================== SCHEDULED ROLLOUT TRIGGER ====================

        @Nested
        @DisplayName("Scheduled Rollout Trigger")
        class ScheduledRollout {

                @Test
                @DisplayName("SCHEDULED rollout triggers when scheduledAt is in the past")
                void triggerScheduledRollout() {
                        UpdateSchedule schedule = UpdateSchedule.builder()
                                        .id(UUID.randomUUID())
                                        .status(ScheduleStatus.APPROVED)
                                        .rolloutType(RolloutType.SCHEDULED)
                                        .scheduledAt(LocalDateTime.now().minusMinutes(10))
                                        .build();

                        DeviceUpdate waitingUpdate = DeviceUpdate.builder()
                                        .id(UUID.randomUUID())
                                        .currentState(UpdateState.SCHEDULED)
                                        .build();

                        when(scheduleRepository.findByStatus(ScheduleStatus.APPROVED))
                                        .thenReturn(List.of(schedule))
                                        .thenReturn(Collections.emptyList()); // Second call after trigger
                        when(scheduleRepository.findByStatus(ScheduleStatus.IN_PROGRESS))
                                        .thenReturn(Collections.emptyList());
                        when(deviceUpdateRepository.findByScheduleIdAndCurrentState(
                                        schedule.getId(), UpdateState.SCHEDULED))
                                        .thenReturn(List.of(waitingUpdate));

                        service.updateScheduleLifecycle();

                        verify(deviceUpdateRepository).saveAll(argThat((List<DeviceUpdate> updates) -> updates.stream()
                                        .allMatch(du -> du.getCurrentState() == UpdateState.NOTIFIED)));
                }
        }
}
