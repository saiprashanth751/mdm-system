package com.moveinsync.mdm.config;

import com.moveinsync.mdm.entity.Admin;
import com.moveinsync.mdm.enums.AdminRole;
import com.moveinsync.mdm.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds initial admin accounts on first startup.
 * Only runs when the admins table is empty — safe to run repeatedly.
 * Uses Spring's PasswordEncoder (BCrypt) to ensure correct hashing.
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (adminRepository.count() > 0) {
            log.info("Admin accounts already exist. Skipping seed.");
            return;
        }

        log.info("No admin accounts found. Seeding initial data...");

        String encodedPassword = passwordEncoder.encode("admin123");

        Admin superAdmin = Admin.builder()
                .username("super_admin")
                .password(encodedPassword)
                .role(AdminRole.SUPER_ADMIN)
                .build();

        Admin releaseEngineer = Admin.builder()
                .username("release_engineer")
                .password(encodedPassword)
                .role(AdminRole.RELEASE_ENGINEER)
                .build();

        Admin opsViewer = Admin.builder()
                .username("ops_viewer")
                .password(encodedPassword)
                .role(AdminRole.OPS_VIEWER)
                .build();

        adminRepository.save(superAdmin);
        adminRepository.save(releaseEngineer);
        adminRepository.save(opsViewer);

        log.info("Seeded 3 admin accounts: super_admin, release_engineer, ops_viewer (password: admin123)");
    }
}
