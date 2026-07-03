package com.lacity.aipppc.service;

import com.lacity.aipppc.exception.ApiException;
import com.lacity.aipppc.model.User;
import com.lacity.aipppc.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Admin user management — grant/revoke the STAFF/ADMIN roles that gate the
 * Review &amp; Analytics and configuration surfaces (role-to-position mapping,
 * SOW deliverable 4.2), and enable/disable accounts. All changes are audited.
 */
@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final AuditService auditService;

    public AdminUserService(UserRepository userRepository, AuditService auditService) {
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    public List<User> listAll() {
        return userRepository.findAll();
    }

    @Transactional
    public User setRole(User admin, UUID userId, String role) {
        User user = require(userId);
        User.Role newRole;
        try {
            newRole = User.Role.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw ApiException.badRequest("Invalid role: " + role + " (APPLICANT, STAFF, or ADMIN)");
        }
        User.Role oldRole = user.getRole();
        user.setRole(newRole);
        userRepository.save(user);
        auditService.recordUser(admin.getEmail(), "USER_ROLE_CHANGED", "User", userId.toString(),
            "role=" + newRole, Map.of("role", oldRole.name()), Map.of("role", newRole.name()));
        return user;
    }

    @Transactional
    public User setEnabled(User admin, UUID userId, boolean enabled) {
        User user = require(userId);
        boolean wasEnabled = user.isEnabled();
        user.setEnabled(enabled);
        userRepository.save(user);
        auditService.recordUser(admin.getEmail(), "USER_ENABLED_CHANGED", "User", userId.toString(),
            "enabled=" + enabled, Map.of("enabled", wasEnabled), Map.of("enabled", enabled));
        return user;
    }

    private User require(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("User not found"));
    }
}
