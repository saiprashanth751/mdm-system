package com.moveinsync.mdm.service;

import com.moveinsync.mdm.dto.request.CreateVersionRequest;
import com.moveinsync.mdm.dto.response.VersionResponse;
import com.moveinsync.mdm.entity.AppVersion;
import com.moveinsync.mdm.enums.ActorType;
import com.moveinsync.mdm.enums.AuditEntityType;
import com.moveinsync.mdm.exception.VersionAlreadyExistsException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import com.moveinsync.mdm.repository.AppVersionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppVersionService {

        private static final Logger log = LoggerFactory.getLogger(AppVersionService.class);
        private final AppVersionRepository appVersionRepository;
        private final AuditService auditService;

        @CacheEvict(value = "versions", allEntries = true)
        @Transactional
        public VersionResponse createVersion(CreateVersionRequest request, UUID adminId) {
                // Immutability check: version code must be unique
                if (appVersionRepository.existsByVersionCode(request.getVersionCode())) {
                        throw new VersionAlreadyExistsException(
                                        "Version with code " + request.getVersionCode()
                                                        + " already exists and cannot be modified.");
                }

                AppVersion version = AppVersion.builder()
                                .versionCode(request.getVersionCode())
                                .versionName(request.getVersionName())
                                .releaseDate(request.getReleaseDate())
                                .minOsVersion(request.getMinOsVersion())
                                .maxOsVersion(request.getMaxOsVersion())
                                .customizationTag(request.getCustomizationTag() != null ? request.getCustomizationTag()
                                                : "GENERIC")
                                .isMandatory(request.getIsMandatory() != null ? request.getIsMandatory() : false)
                                .build();

                version = appVersionRepository.save(version);

                auditService.logAction(AuditEntityType.VERSION, version.getId(),
                                "VERSION_PUBLISHED", adminId, ActorType.ADMIN,
                                Map.of("versionCode", request.getVersionCode(), "versionName",
                                                request.getVersionName()));

                log.info("Version published: code={}, name={} by admin {}",
                                request.getVersionCode(), request.getVersionName(), adminId);

                return toResponse(version,
                                "Version " + version.getVersionName()
                                                + " published successfully. This version is now immutable.");
        }

        @Cacheable(value = "versions", key = "'all'")
        @Transactional(readOnly = true)
        public List<VersionResponse> listVersions() {
                return appVersionRepository.findAllByOrderByVersionCodeDesc()
                                .stream()
                                .map(v -> toResponse(v, null))
                                .collect(Collectors.toList());
        }

        private VersionResponse toResponse(AppVersion v, String message) {
                return VersionResponse.builder()
                                .versionId(v.getId())
                                .versionCode(v.getVersionCode())
                                .versionName(v.getVersionName())
                                .releaseDate(v.getReleaseDate())
                                .minOsVersion(v.getMinOsVersion())
                                .maxOsVersion(v.getMaxOsVersion())
                                .customizationTag(v.getCustomizationTag())
                                .isMandatory(v.getIsMandatory())
                                .isActive(v.getIsActive())
                                .publishedAt(v.getCreatedAt())
                                .message(message)
                                .build();
        }
}
