package com.moveinsync.mdm.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ScheduleUpdateRequest {

    @NotNull(message = "From version code is required")
    @Positive
    private Integer fromVersionCode;

    @NotNull(message = "To version code is required")
    @Positive
    private Integer toVersionCode;

    private String targetRegion;
    private String targetClientTag;

    @NotNull(message = "Rollout type is required")
    private String rolloutType; // IMMEDIATE, SCHEDULED, PHASED

    @Min(value = 1, message = "Rollout percentage must be between 1 and 100")
    @Max(value = 100, message = "Rollout percentage must be between 1 and 100")
    private Integer rolloutPercentage;

    private LocalDateTime scheduledAt;
}
