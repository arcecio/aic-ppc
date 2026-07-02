package com.lacity.aipppc.service;

import com.lacity.aipppc.model.User;
import com.lacity.aipppc.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

/**
 * Idempotently promotes a configured email to ADMIN on every boot. Represents the
 * City granting an initial staff/admin identity (in production via Okta group
 * membership). No-op if the address is blank, unregistered, or already ADMIN — so
 * a fresh database never locks the operator out of the staff/admin surfaces.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;

    @Value("${app.bootstrap.admin-email:}")
    private String adminEmail;

    public AdminBootstrapRunner(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminEmail.isBlank()) {
            return;
        }
        String email = adminEmail.trim().toLowerCase();
        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.getRole() != User.Role.ADMIN) {
                user.setRole(User.Role.ADMIN);
                userRepository.save(user);
                log.info("Bootstrap: promoted {} to ADMIN", email);
            }
        });
    }
}
