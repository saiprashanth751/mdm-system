package com.moveinsync.mdm.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CompatibilityRuleRequest {

    @NotNull(message = "From version code is required")
    @Positive
    private Integer fromVersionCode;

    @NotNull(message = "To version code is required")
    @Positive
    private Integer toVersionCode;

    private Boolean requiresIntermediate;
    private Integer intermediateVersionCode;
}
