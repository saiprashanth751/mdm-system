package com.moveinsync.mdm.kafka;

import com.moveinsync.mdm.config.KafkaConfig;
import com.moveinsync.mdm.entity.DeviceUpdate;
import com.moveinsync.mdm.enums.ActorType;
import com.moveinsync.mdm.enums.AuditEntityType;
import com.moveinsync.mdm.enums.UpdateState;
import com.moveinsync.mdm.event.ScheduleApprovedEvent;
import com.moveinsync.mdm.repository.DeviceUpdateRepository;
import com.moveinsync.mdm.service.AuditService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka consumer for schedule approval events.
 *
 * When an admin approves a schedule, the approval HTTP response returns
 * immediately.
 * This consumer picks up the event asynchronously and transitions all targeted
 * device updates from SCHEDULED → NOTIFIED in batches.
 *
 * Why Kafka instead of Spring ApplicationEvents:
 * - Survives JVM crashes (messages are persistent and replayed)
 * - Scales horizontally across multiple app instances
 * - Provides at-least-once delivery guarantee
 * - This is how MoveInSync production systems work
 *
 * Batch size of 500 chosen to balance:
 * - DB connection hold time (smaller batches = shorter transactions)
 * - Throughput (larger batches = fewer round-trips)
 * - Memory usage (500 entities ≈ 50KB, well within heap limits)
 */
@Component
@RequiredArgsConstructor
public class ScheduleApprovalConsumer {

    private static final Logger log = LoggerFactory.getLogger(ScheduleApprovalConsumer.class);
    private static final int BATCH_SIZE = 500;

    private final DeviceUpdateRepository deviceUpdateRepository;
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;

    @KafkaListener(topics = KafkaConfig.SCHEDULE_APPROVED_TOPIC, groupId = "mdm-schedule-group")
    @Transactional
    public void handleScheduleApproved(ScheduleApprovedEvent event) {
        UUID scheduleId = event.getScheduleId();
        log.info("Processing schedule approval event: scheduleId={}, approvedBy={}",
                scheduleId, event.getApprovedBy());

        try {
            List<DeviceUpdate> allUpdates = deviceUpdateRepository.findByScheduleIdAndCurrentState(
                    scheduleId, UpdateState.SCHEDULED);

            if (allUpdates.isEmpty()) {
                log.warn("No SCHEDULED device updates found for schedule {}. Already processed?", scheduleId);
                return;
            }

            int totalNotified = 0;

            // Process in batches to control transaction size and memory
            for (int i = 0; i < allUpdates.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, allUpdates.size());
                List<DeviceUpdate> batch = allUpdates.subList(i, end);

                batch.forEach(du -> du.setCurrentState(UpdateState.NOTIFIED));
                deviceUpdateRepository.saveAll(batch);

                totalNotified += batch.size();
                log.debug("Batch processed: {}/{} devices notified for schedule {}",
                        totalNotified, allUpdates.size(), scheduleId);
            }

            // Audit the async completion
            auditService.logEvent(AuditEntityType.SCHEDULE, scheduleId,
                    "DEVICES_NOTIFIED_ASYNC", "SCHEDULED", "NOTIFIED",
                    event.getApprovedBy(), ActorType.SYSTEM,
                    Map.of("totalDevicesNotified", totalNotified,
                            "processedAsync", true,
                            "batchSize", BATCH_SIZE));

            // Prometheus: track async notifications
            meterRegistry.counter("mdm.kafka.schedule.processed.total").increment();
            meterRegistry.counter("mdm.kafka.devices.notified.total").increment(totalNotified);

            log.info("Schedule {} approval processed: {} devices notified asynchronously",
                    scheduleId, totalNotified);

        } catch (Exception e) {
            log.error("Failed to process schedule approval for {}: {}", scheduleId, e.getMessage(), e);
            meterRegistry.counter("mdm.kafka.schedule.failed.total").increment();
            // Kafka will retry based on consumer configuration
            throw e;
        }
    }
}
