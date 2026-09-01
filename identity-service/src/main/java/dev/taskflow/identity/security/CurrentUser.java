package dev.taskflow.identity.security;

import dev.taskflow.identity.web.ApiException;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static TokenClaims require() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof TokenClaims claims)) {
            throw ApiException.unauthorized("Sign in to continue.");
        }
        return claims;
    }

    public static TokenClaims requireAdmin(String message) {
        TokenClaims claims = require();
        if (!claims.isAdmin()) {
            throw ApiException.forbidden(message);
        }
        return claims;
    }
}
