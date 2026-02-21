package com.moveinsync.mdm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class ScheduleResponse {
    private UUID scheduleId;
    private String status;
    private Integer fromVersionCode;
    private Integer toVersionCode;
    private String targetRegion;
    private String targetClientTag;
    private String rolloutType;
    private Integer rolloutPercentage;
    private Long targetDeviceCount;
    private Long initialBatchSize;
    private LocalDateTime scheduledAt;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private String message;
}
