package com.moveinsync.mdm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Defines allowed version upgrade paths.
 * Forms a directed graph: from_version → to_version.
 * Used for BFS path finding in upgrade validation.
 */
@Entity
@Table(name = "version_compatibility", uniqueConstraints = @UniqueConstraint(columnNames = { "from_version_code",
        "to_version_code" }))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionCompatibility {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "from_version_code", nullable = false)
    private Integer fromVersionCode;

    @Column(name = "to_version_code", nullable = false)
    private Integer toVersionCode;

    @Column(name = "requires_intermediate")
    @Builder.Default
    private Boolean requiresIntermediate = false;

    @Column(name = "intermediate_version_code")
    private Integer intermediateVersionCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
