package com.moveinsync.mdm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DeviceRegisterRequest {

    @NotBlank(message = "IMEI is required")
    @Size(min = 15, max = 20, message = "IMEI must be between 15 and 20 characters")
    private String imei;

    @NotBlank(message = "App version is required")
    private String appVersion;

    private String deviceOs;
    private String deviceModel;
    private String region;
    private String clientTag;
}
