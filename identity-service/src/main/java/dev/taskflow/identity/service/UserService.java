package dev.taskflow.identity.service;

import dev.taskflow.identity.domain.AppUser;
import dev.taskflow.identity.repo.AppUserRepository;
import dev.taskflow.identity.security.TokenClaims;
import dev.taskflow.identity.web.ApiException;
import dev.taskflow.identity.web.Dtos.UpdateMeRequest;
import dev.taskflow.identity.web.Dtos.UpdateUserRequest;
import dev.taskflow.identity.web.Dtos.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserService(AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public UserResponse me(TokenClaims claims) {
        return UserResponse.from(load(claims.userId()));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listInOrg(TokenClaims claims) {
        return users.findAllByOrganizationIdOrderByNameAsc(claims.orgId())
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional
    public UserResponse updateMe(TokenClaims claims, UpdateMeRequest request) {
        AppUser user = load(claims.userId());

        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw ApiException.unprocessable("Your name cannot be empty.");
            }
            user.setName(request.name().trim());
        }
        if (request.timezone() != null && !request.timezone().isBlank()) {
            user.setTimezone(request.timezone());
        }
        if (request.password() != null && !request.password().isBlank()) {
            if (request.password().length() < 8) {
                throw ApiException.unprocessable(
                        "Use at least 8 characters for the new password.");
            }
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateAsAdmin(TokenClaims claims, UUID targetId,
                                      UpdateUserRequest request) {
        AppUser target = load(targetId);
        if (!target.getOrganization().getId().equals(claims.orgId())) {
            throw ApiException.notFound("That member no longer exists.");
        }
        if (target.getId().equals(claims.userId())) {
            throw ApiException.unprocessable("You cannot change your own role or status.");
        }
        if (request.role() != null) {
            target.setRole(request.role());
        }
        if (request.status() != null) {
            target.setStatus(request.status());
        }
        return UserResponse.from(target);
    }

    private AppUser load(UUID id) {
        return users.findById(id)
                .orElseThrow(() -> ApiException.notFound("That member no longer exists."));
    }
}
