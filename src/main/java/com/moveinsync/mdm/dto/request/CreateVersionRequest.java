package com.moveinsync.mdm.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateVersionRequest {

    @NotNull(message = "Version code is required")
    @Positive(message = "Version code must be positive")
    private Integer versionCode;

    @NotNull(message = "Version name is required")
    private String versionName;

    private LocalDate releaseDate;
    private String minOsVersion;
    private String maxOsVersion;
    private String customizationTag;
    private Boolean isMandatory;
}
