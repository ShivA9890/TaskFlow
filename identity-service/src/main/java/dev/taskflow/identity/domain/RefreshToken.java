package dev.taskflow.identity.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
public class RefreshToken {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static RefreshToken issue(AppUser user, String tokenHash, Instant expiresAt) {
        RefreshToken token = new RefreshToken();
        token.id = UUID.randomUUID();
        token.user = user;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;
        token.revoked = false;
        token.createdAt = Instant.now();
        return token;
    }

    public boolean isUsable() {
        return !revoked && expiresAt.isAfter(Instant.now());
    }
}
