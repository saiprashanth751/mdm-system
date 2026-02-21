package com.moveinsync.mdm.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class HeartbeatRequest {

    @NotBlank(message = "IMEI is required")
    private String imei;

    @NotBlank(message = "App version is required")
    private String appVersion;

    private String region;
}
