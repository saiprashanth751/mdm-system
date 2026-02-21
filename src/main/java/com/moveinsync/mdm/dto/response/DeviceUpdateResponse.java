package com.moveinsync.mdm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class DeviceUpdateResponse {
    private UUID deviceUpdateId;
    private String previousState;
    private String currentState;
    private LocalDateTime updatedAt;
    private String message;
}
