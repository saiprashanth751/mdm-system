package com.moveinsync.mdm.controller;

import com.moveinsync.mdm.dto.request.CompatibilityRuleRequest;
import com.moveinsync.mdm.dto.request.CreateVersionRequest;
import com.moveinsync.mdm.dto.response.CompatibilityCheckResponse;
import com.moveinsync.mdm.dto.response.VersionResponse;
import com.moveinsync.mdm.entity.Admin;
import com.moveinsync.mdm.repository.AdminRepository;
import com.moveinsync.mdm.service.AppVersionService;
import com.moveinsync.mdm.service.VersionCompatibilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/versions")
@RequiredArgsConstructor
@Tag(name = "App Versions", description = "Version management and compatibility endpoints")
public class AppVersionController {

    private final AppVersionService appVersionService;
    private final VersionCompatibilityService compatibilityService;
    private final AdminRepository adminRepository;

    @PostMapping
    @Operation(summary = "Publish Version", description = "Publish a new app version. Immutable once created. Requires RELEASE_ENGINEER or SUPER_ADMIN role.")
    public ResponseEntity<VersionResponse> createVersion(
            @Valid @RequestBody CreateVersionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID adminId = getAdminId(userDetails);
        return ResponseEntity.status(HttpStatus.CREATED).body(appVersionService.createVersion(request, adminId));
    }

    @GetMapping
    @Operation(summary = "List Versions", description = "List all published versions, sorted by version code descending.")
    public ResponseEntity<List<VersionResponse>> listVersions() {
        return ResponseEntity.ok(appVersionService.listVersions());
    }

    @PostMapping("/compatibility")
    @Operation(summary = "Create Compatibility Rule", description = "Define an upgrade path between two versions. Downgrades are blocked.")
    public ResponseEntity<Map<String, String>> createCompatibilityRule(
            @Valid @RequestBody CompatibilityRuleRequest request) {
        String message = compatibilityService.createRule(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", message));
    }

    @GetMapping("/compatibility/check")
    @Operation(summary = "Check Upgrade Path", description = "Check if a valid upgrade path exists between two versions using BFS.")
    public ResponseEntity<CompatibilityCheckResponse> checkUpgradePath(
            @RequestParam Integer from,
            @RequestParam Integer to) {
        return ResponseEntity.ok(compatibilityService.checkUpgradePath(from, to));
    }

    private UUID getAdminId(UserDetails userDetails) {
        Admin admin = adminRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));
        return admin.getId();
    }
}
