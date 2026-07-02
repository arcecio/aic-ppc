package com.lacity.aipppc.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A person using the assistant. Roles map to the RFP's user classes (SOW 2.2.15):
 * <ul>
 *   <li>{@code APPLICANT} — external users (Angeleno Account / Auth0).</li>
 *   <li>{@code STAFF} — City reviewers (Okta SSO). Access the Review &amp; Analytics mode.</li>
 *   <li>{@code ADMIN} — administrators; superset of STAFF plus rule/config/API management.</li>
 * </ul>
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private Role role = Role.APPLICANT;

    /** Optional org/company for external applicants (architect, developer, etc.). */
    @Column(name = "organization")
    private String organization;

    @Column(nullable = false)
    private boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum Role { APPLICANT, STAFF, ADMIN }

    public User() {}

    /** True for City reviewers and admins — gates the staff Review &amp; Analytics mode. */
    public boolean isStaff() {
        return role == Role.STAFF || role == Role.ADMIN;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getOrganization() { return organization; }
    public void setOrganization(String organization) { this.organization = organization; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final User u = new User();

        public Builder id(UUID id) { u.id = id; return this; }
        public Builder email(String email) { u.email = email; return this; }
        public Builder passwordHash(String passwordHash) { u.passwordHash = passwordHash; return this; }
        public Builder name(String name) { u.name = name; return this; }
        public Builder role(Role role) { u.role = role; return this; }
        public Builder organization(String organization) { u.organization = organization; return this; }
        public Builder enabled(boolean enabled) { u.enabled = enabled; return this; }
        public Builder createdAt(Instant createdAt) { u.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { u.updatedAt = updatedAt; return this; }

        public User build() { return u; }
    }
}
