package dev.taskflow.identity.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
public class AppUser {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_id", nullable = false)
    private Organization organization;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserStatus status;

    @Column(nullable = false)
    private String timezone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static AppUser create(Organization org, String email, String passwordHash,
                                 String name, Role role, String timezone) {
        AppUser user = new AppUser();
        user.id = UUID.randomUUID();
        user.organization = org;
        user.email = email.trim().toLowerCase();
        user.passwordHash = passwordHash;
        user.name = name;
        user.role = role;
        user.status = UserStatus.ACTIVE;
        user.timezone = timezone;
        user.createdAt = Instant.now();
        return user;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
