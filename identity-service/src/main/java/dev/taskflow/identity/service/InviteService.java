package dev.taskflow.identity.service;

import dev.taskflow.identity.domain.*;
import dev.taskflow.identity.repo.AppUserRepository;
import dev.taskflow.identity.repo.InviteRepository;
import dev.taskflow.identity.repo.OrganizationRepository;
import dev.taskflow.identity.security.InviteProperties;
import dev.taskflow.identity.security.TokenClaims;
import dev.taskflow.identity.security.Tokens;
import dev.taskflow.identity.web.ApiException;
import dev.taskflow.identity.web.Dtos.AcceptInviteRequest;
import dev.taskflow.identity.web.Dtos.CreateInviteRequest;
import dev.taskflow.identity.web.Dtos.InviteResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class InviteService {

    private final InviteRepository invites;
    private final AppUserRepository users;
    private final OrganizationRepository organizations;
    private final InviteProperties props;
    private final OutboxPublisher outbox;
    private final AuthServiceSupport authSupport;

    public InviteService(InviteRepository invites,
                         AppUserRepository users,
                         OrganizationRepository organizations,
                         InviteProperties props,
                         OutboxPublisher outbox,
                         AuthServiceSupport authSupport) {
        this.invites = invites;
        this.users = users;
        this.organizations = organizations;
        this.props = props;
        this.outbox = outbox;
        this.authSupport = authSupport;
    }

    @Transactional(readOnly = true)
    public List<InviteResponse> list(TokenClaims claims) {
        return invites.findAllByOrganizationIdOrderByCreatedAtDesc(claims.orgId())
                .stream()
                .map(InviteResponse::from)
                .toList();
    }

    @Transactional
    public InviteResponse create(TokenClaims claims, CreateInviteRequest request) {
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw ApiException.conflict("That person is already a member.");
        }
        Organization org = organizations.findById(claims.orgId())
                .orElseThrow(() -> ApiException.notFound("That workspace no longer exists."));

        String rawToken = Tokens.generate();
        Invite invite = invites.save(Invite.create(
                org,
                request.email(),
                request.role(),
                Tokens.hash(rawToken),
                Instant.now().plus(props.ttl())));

        String acceptUrl = props.acceptBaseUrl() + "?token=" + rawToken;

        // Committed with the invite row. The poller turns this into an SNS message,
        // which notification-service turns into an email.
        outbox.publish("user.invited", Map.of(
                "inviteId", invite.getId().toString(),
                "orgId", org.getId().toString(),
                "orgName", org.getName(),
                "email", invite.getEmail(),
                "role", invite.getRole().name(),
                "acceptUrl", acceptUrl,
                "expiresAt", invite.getExpiresAt().toString()));

        return InviteResponse.from(invite, acceptUrl);
    }

    @Transactional
    public dev.taskflow.identity.web.Dtos.AuthResponse accept(AcceptInviteRequest request) {
        Invite invite = invites.findByTokenHash(Tokens.hash(request.token()))
                .orElseThrow(() -> ApiException.notFound(
                        "This invite link is not valid or has already been used."));

        if (invite.getAcceptedAt() != null) {
            throw ApiException.notFound(
                    "This invite link is not valid or has already been used.");
        }
        if (invite.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(org.springframework.http.HttpStatus.GONE,
                    "This invite has expired.");
        }
        if (users.existsByEmailIgnoreCase(invite.getEmail())) {
            throw ApiException.conflict("That person is already a member.");
        }

        AppUser user = users.save(AppUser.create(
                invite.getOrganization(),
                invite.getEmail(),
                authSupport.encode(request.password()),
                request.name().trim(),
                invite.getRole(),
                "UTC"));

        invite.setAcceptedAt(Instant.now());
        return authSupport.issueTokens(user);
    }
}
