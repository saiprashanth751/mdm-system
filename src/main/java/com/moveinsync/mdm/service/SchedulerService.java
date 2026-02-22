package com.moveinsync.mdm.service;

import com.moveinsync.mdm.entity.Device;
import com.moveinsync.mdm.entity.DeviceUpdate;
import com.moveinsync.mdm.entity.UpdateSchedule;
import com.moveinsync.mdm.enums.*;
import com.moveinsync.mdm.repository.DeviceRepository;
import com.moveinsync.mdm.repository.DeviceUpdateRepository;
import com.moveinsync.mdm.repository.UpdateScheduleRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Scheduled tasks for:
 * 1. Inactive device detection — marks devices as INACTIVE if no heartbeat
 * within threshold
 * 2. Auto-retry — reschedules FAILED device updates up to max retry count
 * 3. Schedule lifecycle — transitions schedules: APPROVED → IN_PROGRESS →
 * COMPLETED
 */
@Service
@RequiredArgsConstructor
public class SchedulerService {

        private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);

        private final DeviceRepository deviceRepository;
        private final DeviceUpdateRepository deviceUpdateRepository;
        private final UpdateScheduleRepository scheduleRepository;
        private final AuditService auditService;
        private final MeterRegistry meterRegistry;

        @Value("${app.scheduler.inactive-threshold-days:7}")
        private int inactiveThresholdDays;

        @Value("${app.scheduler.max-retry-count:3}")
        private int maxRetryCount;

        // ═══════════════════════════════════════════════════════════
        // GAP #2 FIX: Inactive Device Detection
        // Runs every hour. Marks devices as INACTIVE if no heartbeat
        // has been received within the configured threshold.
        // ═══════════════════════════════════════════════════════════
        @Scheduled(fixedRateString = "${app.scheduler.inactive-check-interval-ms:3600000}")
        @Transactional
        @CacheEvict(value = "dashboard", allEntries = true)
        public void detectInactiveDevices() {
                LocalDateTime threshold = LocalDateTime.now().minusDays(inactiveThresholdDays);
                List<Device> inactiveDevices = deviceRepository.findByLastHeartbeatBeforeAndStatus(
                                threshold, DeviceStatus.ACTIVE);

                if (inactiveDevices.isEmpty()) {
                        log.debug("Inactive device check: no devices to mark inactive (threshold: {} days)",
                                        inactiveThresholdDays);
                        return;
                }

                int count = 0;
                for (Device device : inactiveDevices) {
                        device.setStatus(DeviceStatus.INACTIVE);
                        deviceRepository.save(device);

                        auditService.logEvent(AuditEntityType.DEVICE, device.getId(),
                                        "MARKED_INACTIVE", "ACTIVE", "INACTIVE",
                                        null, ActorType.SYSTEM,
                                        Map.of("reason", "No heartbeat for " + inactiveThresholdDays + " days",
                                                        "lastHeartbeat", device.getLastHeartbeat() != null
                                                                        ? device.getLastHeartbeat().toString()
                                                                        : "never"));
                        count++;
                }

                // Prometheus: track inactive device marking
                meterRegistry.counter("mdm.device.inactive.marked").increment(count);

                log.info("Inactive device check completed: marked {} device(s) as INACTIVE (threshold: {} days)",
                                count, inactiveThresholdDays);
        }

        // ═══════════════════════════════════════════════════════════
        // GAP #3 FIX: Auto-Retry Failed Updates
        // Runs every 30 minutes. Finds FAILED device updates with
        // retry_count < max and resets them to SCHEDULED.
        // ═══════════════════════════════════════════════════════════
        @Scheduled(fixedRateString = "${app.scheduler.retry-check-interval-ms:1800000}")
        @Transactional
        public void retryFailedUpdates() {
                List<DeviceUpdate> failedUpdates = deviceUpdateRepository.findByCurrentStateAndRetryCountLessThan(
                                UpdateState.FAILED, maxRetryCount);

                if (failedUpdates.isEmpty()) {
                        log.debug("Retry check: no failed updates eligible for retry (maxRetries: {})", maxRetryCount);
                        return;
                }

                int retried = 0;
                for (DeviceUpdate update : failedUpdates) {
                        String previousState = update.getCurrentState().name();
                        update.setCurrentState(UpdateState.SCHEDULED);
                        update.setFailureStage(null);
                        update.setFailureReason(null);
                        deviceUpdateRepository.save(update);

                        auditService.logEvent(AuditEntityType.DEVICE_UPDATE, update.getId(),
                                        "AUTO_RETRY", previousState, "SCHEDULED",
                                        null, ActorType.SYSTEM,
                                        Map.of("retryCount", update.getRetryCount(),
                                                        "maxRetries", maxRetryCount,
                                                        "previousFailureStage", previousState));
                        retried++;
                }

                // Prometheus: track retries
                meterRegistry.counter("mdm.update.retry.total").increment(retried);

                log.info("Retry check completed: rescheduled {} failed update(s) (maxRetries: {})",
                                retried, maxRetryCount);
        }

        // ═══════════════════════════════════════════════════════════
        // GAP #4 FIX: Schedule Lifecycle State Transitions
        // Runs every 5 minutes. Transitions schedules:
        // APPROVED → IN_PROGRESS (when any device update has progressed)
        // IN_PROGRESS → COMPLETED (when ALL device updates are terminal)
        // ═══════════════════════════════════════════════════════════
        @Scheduled(fixedRate = 300000) // 5 minutes
        @Transactional
        @CacheEvict(value = "dashboard", allEntries = true)
        public void updateScheduleLifecycle() {
                // ═══════════════════════════════════════════════════════════
                // FIX #4: Trigger time-based scheduled rollouts
                // When an APPROVED schedule has rolloutType=SCHEDULED and
                // scheduledAt is in the past, notify waiting devices.
                // ═══════════════════════════════════════════════════════════
                List<UpdateSchedule> approved = scheduleRepository.findByStatus(ScheduleStatus.APPROVED);
                for (UpdateSchedule schedule : approved) {
                        if (schedule.getRolloutType() == RolloutType.SCHEDULED
                                        && schedule.getScheduledAt() != null
                                        && schedule.getScheduledAt().isBefore(LocalDateTime.now())) {
                                List<DeviceUpdate> waiting = deviceUpdateRepository.findByScheduleIdAndCurrentState(
                                                schedule.getId(), UpdateState.SCHEDULED);
                                if (!waiting.isEmpty()) {
                                        waiting.forEach(du -> du.setCurrentState(UpdateState.NOTIFIED));
                                        deviceUpdateRepository.saveAll(waiting);
                                        auditService.logEvent(AuditEntityType.SCHEDULE, schedule.getId(),
                                                        "SCHEDULED_ROLLOUT_TRIGGERED", "APPROVED", "APPROVED",
                                                        null, ActorType.SYSTEM,
                                                        Map.of("reason", "Scheduled time reached",
                                                                        "devicesNotified", waiting.size()));
                                        meterRegistry.counter("mdm.rollout.scheduled.triggered").increment();
                                        log.info("Scheduled rollout triggered for schedule {} — {} devices notified",
                                                        schedule.getId(), waiting.size());
                                }
                        }
                }

                // APPROVED → IN_PROGRESS when any device update has progressed beyond SCHEDULED
                // (Re-fetch approved schedules since some may have been modified above)
                approved = scheduleRepository.findByStatus(ScheduleStatus.APPROVED);
                for (UpdateSchedule schedule : approved) {
                        long total = deviceUpdateRepository.countByScheduleId(schedule.getId());
                        long stillScheduled = deviceUpdateRepository.countByScheduleIdAndCurrentState(
                                        schedule.getId(), UpdateState.SCHEDULED);

                        // If at least one device has progressed beyond SCHEDULED, mark IN_PROGRESS
                        if (total > 0 && stillScheduled < total) {
                                schedule.setStatus(ScheduleStatus.IN_PROGRESS);
                                scheduleRepository.save(schedule);
                                auditService.logEvent(AuditEntityType.SCHEDULE, schedule.getId(),
                                                "STATUS_CHANGED", "APPROVED", "IN_PROGRESS",
                                                null, ActorType.SYSTEM,
                                                Map.of("reason", "Device updates have begun processing"));
                                log.info("Schedule {} transitioned: APPROVED → IN_PROGRESS", schedule.getId());
                        }
                }

                // IN_PROGRESS → COMPLETED when all device updates are terminal
                List<UpdateSchedule> inProgress = scheduleRepository.findByStatus(ScheduleStatus.IN_PROGRESS);
                for (UpdateSchedule schedule : inProgress) {
                        long total = deviceUpdateRepository.countByScheduleId(schedule.getId());
                        long completed = deviceUpdateRepository.countByScheduleIdAndCurrentState(
                                        schedule.getId(), UpdateState.INSTALLATION_COMPLETED);
                        long permanentlyFailed = deviceUpdateRepository
                                        .countByScheduleIdAndCurrentStateAndRetryCountGreaterThanEqual(
                                                        schedule.getId(), UpdateState.FAILED, maxRetryCount);

                        // All devices are either completed or permanently failed
                        if (total > 0 && (completed + permanentlyFailed) >= total) {
                                schedule.setStatus(ScheduleStatus.COMPLETED);
                                scheduleRepository.save(schedule);
                                auditService.logEvent(AuditEntityType.SCHEDULE, schedule.getId(),
                                                "STATUS_CHANGED", "IN_PROGRESS", "COMPLETED",
                                                null, ActorType.SYSTEM,
                                                Map.of("completedDevices", completed,
                                                                "failedDevices", permanentlyFailed,
                                                                "totalDevices", total));
                                log.info("Schedule {} transitioned: IN_PROGRESS → COMPLETED ({}/{} succeeded)",
                                                schedule.getId(), completed, total);
                        }
                }
        }
}
