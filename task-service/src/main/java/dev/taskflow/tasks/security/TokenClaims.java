package dev.taskflow.tasks.security;

import java.util.List;
import java.util.UUID;

/**
 * Everything authorization needs, read straight from the verified token.
 * No call back to identity-service on any request path.
 */
public record TokenClaims(
        UUID userId,
        UUID orgId,
        String role,
        List<UUID> teamIds
) {
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
