package com.moveinsync.mdm.service;

import com.moveinsync.mdm.dto.request.AdminRegisterRequest;
import com.moveinsync.mdm.dto.response.AdminResponse;
import com.moveinsync.mdm.entity.Admin;
import com.moveinsync.mdm.enums.ActorType;
import com.moveinsync.mdm.enums.AuditEntityType;
import com.moveinsync.mdm.enums.AdminRole;
import com.moveinsync.mdm.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Gap #13 FIX: AdminService — manages admin users.
 * Provides registration capability referenced in README and SecurityConfig.
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional
    public AdminResponse registerAdmin(AdminRegisterRequest request, UUID creatorId) {
        if (adminRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username '" + request.getUsername() + "' already exists.");
        }

        AdminRole role;
        try {
            role = AdminRole.valueOf(request.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid role: " + request.getRole() +
                    ". Valid roles: SUPER_ADMIN, RELEASE_ENGINEER, OPS_VIEWER");
        }

        Admin admin = Admin.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .build();

        admin = adminRepository.save(admin);

        auditService.logAction(AuditEntityType.ADMIN_ACTION, admin.getId(),
                "ADMIN_REGISTERED", creatorId, ActorType.ADMIN,
                Map.of("username", admin.getUsername(), "role", role.name()));

        log.info("Admin registered: username={}, role={} by admin {}",
                admin.getUsername(), role, creatorId);

        return AdminResponse.builder()
                .adminId(admin.getId())
                .username(admin.getUsername())
                .role(admin.getRole().name())
                .message("Admin '" + admin.getUsername() + "' registered successfully with role " + role.name())
                .build();
    }

    @Transactional(readOnly = true)
    public List<AdminResponse> listAdmins() {
        return adminRepository.findAll().stream()
                .map(admin -> AdminResponse.builder()
                        .adminId(admin.getId())
                        .username(admin.getUsername())
                        .role(admin.getRole().name())
                        .build())
                .collect(Collectors.toList());
    }
}
