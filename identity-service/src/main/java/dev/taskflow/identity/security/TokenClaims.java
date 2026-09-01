package dev.taskflow.identity.security;

import dev.taskflow.identity.domain.Role;

import java.util.List;
import java.util.UUID;

/**
 * What the other services get to read out of an access token without calling us.
 * task-service authorizes entirely from these claims — no network hop per request.
 */
public record TokenClaims(
        UUID userId,
        UUID orgId,
        Role role,
        List<UUID> teamIds
) {
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
