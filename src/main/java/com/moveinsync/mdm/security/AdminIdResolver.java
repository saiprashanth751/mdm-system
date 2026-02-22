package com.moveinsync.mdm.security;

import com.moveinsync.mdm.entity.Admin;
import com.moveinsync.mdm.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Gap #21 FIX: Shared utility to resolve admin UUID from JWT-authenticated
 * UserDetails.
 * Replaces duplicated private getAdminId() methods across controllers.
 */
@Component
@RequiredArgsConstructor
public class AdminIdResolver {

    private final AdminRepository adminRepository;

    /**
     * Resolves the admin's UUID from the authenticated UserDetails (JWT principal).
     * 
     * @throws IllegalArgumentException if the admin user is not found in the
     *                                  database
     */
    public UUID resolve(UserDetails userDetails) {
        Admin admin = adminRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Admin not found for username: " + userDetails.getUsername()));
        return admin.getId();
    }
}
