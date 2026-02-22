package com.moveinsync.mdm.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event published to Kafka when a schedule is approved.
 *
 * The HTTP response returns immediately after publishing this event.
 * The ScheduleApprovalConsumer processes device notifications asynchronously,
 * ensuring the approval API call is fast regardless of how many devices
 * are targeted by the schedule.
 *
 * If the application crashes after publishing but before processing,
 * Kafka guarantees the message is replayed on restart (at-least-once delivery).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScheduleApprovedEvent implements Serializable {

    private UUID scheduleId;
    private UUID approvedBy;
    private LocalDateTime approvedAt;
}
