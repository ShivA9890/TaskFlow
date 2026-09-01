package dev.taskflow.identity.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invites")
@Getter
@Setter
public class Invite {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_id", nullable = false)
    private Organization organization;

    @Column(nullable = false)
    private String email;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static Invite create(Organization org, String email, Role role,
                               String tokenHash, Instant expiresAt) {
        Invite invite = new Invite();
        invite.id = UUID.randomUUID();
        invite.organization = org;
        invite.email = email.trim().toLowerCase();
        invite.role = role;
        invite.tokenHash = tokenHash;
        invite.expiresAt = expiresAt;
        invite.createdAt = Instant.now();
        return invite;
    }

    public boolean isUsable() {
        return acceptedAt == null && expiresAt.isAfter(Instant.now());
    }
}
