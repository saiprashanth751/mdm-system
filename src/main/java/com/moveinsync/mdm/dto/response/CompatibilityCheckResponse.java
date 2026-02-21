package com.moveinsync.mdm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class CompatibilityCheckResponse {
    private boolean allowed;
    private boolean requiresIntermediate;
    private List<Integer> upgradePath;
    private String message;
}
