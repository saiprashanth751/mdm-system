package com.moveinsync.mdm.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateStatusRequest {

    @NotBlank(message = "IMEI is required")
    private String imei;

    @NotBlank(message = "New state is required")
    private String newState;

    // Optional failure details (required when newState = FAILED)
    private String failureReason;
}
