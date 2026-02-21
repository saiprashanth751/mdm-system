package com.moveinsync.mdm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class VersionResponse {
    private UUID versionId;
    private Integer versionCode;
    private String versionName;
    private LocalDate releaseDate;
    private String minOsVersion;
    private String maxOsVersion;
    private String customizationTag;
    private Boolean isMandatory;
    private Boolean isActive;
    private LocalDateTime publishedAt;
    private String message;
}
