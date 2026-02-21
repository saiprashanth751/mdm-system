package com.moveinsync.mdm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a published app version.
 * Immutable once created — no updated_at field by design.
 */
@Entity
@Table(name = "app_versions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "version_code", unique = true, nullable = false)
    private Integer versionCode;

    @Column(name = "version_name", nullable = false, length = 20)
    private String versionName;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "min_os_version", length = 20)
    private String minOsVersion;

    @Column(name = "max_os_version", length = 20)
    private String maxOsVersion;

    @Column(name = "customization_tag", length = 100)
    @Builder.Default
    private String customizationTag = "GENERIC";

    @Column(name = "is_mandatory")
    @Builder.Default
    private Boolean isMandatory = false;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
