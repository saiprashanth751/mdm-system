package com.moveinsync.mdm.entity;

import com.moveinsync.mdm.enums.UpdateState;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tracks one device's progress through an update.
 * State machine: SCHEDULED → NOTIFIED → DOWNLOAD_STARTED → DOWNLOAD_COMPLETED
 * → INSTALLATION_STARTED → INSTALLATION_COMPLETED
 * Any state → FAILED (with failure details captured)
 */
@Entity
@Table(name = "device_updates", uniqueConstraints = @UniqueConstraint(columnNames = { "schedule_id", "device_id" }))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceUpdate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private UpdateSchedule schedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_state", nullable = false, length = 50)
    @Builder.Default
    private UpdateState currentState = UpdateState.SCHEDULED;

    @Column(name = "failure_stage", length = 50)
    private String failureStage;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
