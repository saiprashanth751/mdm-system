package com.moveinsync.mdm.controller;

import com.moveinsync.mdm.dto.request.AdminRegisterRequest;
import com.moveinsync.mdm.dto.request.LoginRequest;
import com.moveinsync.mdm.dto.response.AdminResponse;
import com.moveinsync.mdm.dto.response.LoginResponse;
import com.moveinsync.mdm.entity.Admin;
import com.moveinsync.mdm.repository.AdminRepository;
import com.moveinsync.mdm.security.JwtService;
import com.moveinsync.mdm.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Admin authentication endpoints")
public class AuthController {

        private final AuthenticationManager authenticationManager;
        private final JwtService jwtService;
        private final AdminRepository adminRepository;
        private final AdminService adminService;

        @PostMapping("/login")
        @Operation(summary = "Admin Login", description = "Authenticate with username/password. Returns JWT token with role claims.")
        public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
                Authentication authentication = authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

                Admin admin = adminRepository.findByUsername(request.getUsername())
                                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

                String token = jwtService.generateToken(admin.getUsername(), admin.getRole().name(),
                                admin.getId().toString());

                return ResponseEntity.ok(LoginResponse.builder()
                                .token(token)
                                .role(admin.getRole().name())
                                .expiresIn(jwtService.getJwtExpiration())
                                .build());
        }

        @PostMapping("/register")
        @Operation(summary = "Register Admin", description = "Register a new admin user. Requires SUPER_ADMIN role.")
        public ResponseEntity<AdminResponse> register(
                        @Valid @RequestBody AdminRegisterRequest request,
                        @RequestHeader("X-Admin-Id") UUID adminId) {
                AdminResponse response = adminService.registerAdmin(request, adminId);
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
}
