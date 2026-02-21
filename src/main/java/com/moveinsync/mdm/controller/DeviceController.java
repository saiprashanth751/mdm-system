package com.moveinsync.mdm.controller;

import com.moveinsync.mdm.dto.request.DeviceRegisterRequest;
import com.moveinsync.mdm.dto.request.HeartbeatRequest;
import com.moveinsync.mdm.dto.response.DeviceResponse;
import com.moveinsync.mdm.dto.response.HeartbeatResponse;
import com.moveinsync.mdm.enums.DeviceStatus;
import com.moveinsync.mdm.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
@Tag(name = "Devices", description = "Device management and heartbeat endpoints")
public class DeviceController {

    private final DeviceService deviceService;

    @PostMapping("/register")
    @Operation(summary = "Register Device", description = "Register a new device with IMEI. No authentication required.")
    public ResponseEntity<DeviceResponse> register(@Valid @RequestBody DeviceRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(deviceService.registerDevice(request));
    }

    @PostMapping("/heartbeat")
    @Operation(summary = "Heartbeat", description = "Device heartbeat. Updates last_heartbeat and checks for pending updates.")
    public ResponseEntity<HeartbeatResponse> heartbeat(@Valid @RequestBody HeartbeatRequest request) {
        return ResponseEntity.ok(deviceService.processHeartbeat(request));
    }

    @GetMapping
    @Operation(summary = "List Devices", description = "Paginated device listing with optional filters. Requires authentication.")
    public ResponseEntity<Page<DeviceResponse>> listDevices(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String appVersion,
            @RequestParam(required = false) String clientTag,
            @RequestParam(required = false) DeviceStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(deviceService.listDevices(region, appVersion, clientTag, status, pageable));
    }

    @GetMapping("/{deviceId}")
    @Operation(summary = "Get Device", description = "Get device details by ID. Requires authentication.")
    public ResponseEntity<DeviceResponse> getDevice(@PathVariable UUID deviceId) {
        return ResponseEntity.ok(deviceService.getDeviceById(deviceId));
    }
}
