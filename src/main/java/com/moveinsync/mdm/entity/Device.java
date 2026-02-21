package com.moveinsync.mdm.entity;

import com.moveinsync.mdm.enums.DeviceStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "devices")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false, length = 20)
    private String imei;

    @Column(name = "app_version", nullable = false, length = 20)
    private String appVersion;

    @Column(name = "device_os", length = 50)
    private String deviceOs;

    @Column(name = "device_model", length = 100)
    private String deviceModel;

    @Column(length = 100)
    private String region;

    @Column(name = "client_tag", length = 100)
    private String clientTag;

    @Column(name = "last_heartbeat")
    private LocalDateTime lastHeartbeat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DeviceStatus status = DeviceStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.lastHeartbeat = LocalDateTime.now();
    }
}
