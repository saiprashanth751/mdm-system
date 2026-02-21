package com.moveinsync.mdm.entity;

import com.moveinsync.mdm.enums.RolloutType;
import com.moveinsync.mdm.enums.ScheduleStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents an admin-created update campaign.
 * Tracks the rollout plan: which devices, what version, how to roll out.
 */
@Entity
@Table(name = "update_schedules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private Admin createdBy;

    @Column(name = "from_version_code", nullable = false)
    private Integer fromVersionCode;

    @Column(name = "to_version_code", nullable = false)
    private Integer toVersionCode;

    @Column(name = "target_region", length = 100)
    private String targetRegion;

    @Column(name = "target_client_tag", length = 100)
    private String targetClientTag;

    @Enumerated(EnumType.STRING)
    @Column(name = "rollout_type", nullable = false, length = 20)
    private RolloutType rolloutType;

    @Column(name = "rollout_percentage")
    @Builder.Default
    private Integer rolloutPercentage = 100;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ScheduleStatus status = ScheduleStatus.PENDING_APPROVAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private Admin approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
