package com.moveinsync.mdm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class DeviceResponse {
    private UUID deviceId;
    private String imei;
    private String appVersion;
    private String deviceOs;
    private String deviceModel;
    private String region;
    private String clientTag;
    private LocalDateTime lastHeartbeat;
    private String status;
    private LocalDateTime registeredAt;
    private String message;
}
