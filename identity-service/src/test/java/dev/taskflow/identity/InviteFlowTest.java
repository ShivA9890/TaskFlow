package dev.taskflow.identity;

import dev.taskflow.identity.domain.Role;
import dev.taskflow.identity.repo.AppUserRepository;
import dev.taskflow.identity.repo.InviteRepository;
import dev.taskflow.identity.repo.OrganizationRepository;
import dev.taskflow.identity.repo.OutboxEventRepository;
import dev.taskflow.identity.repo.RefreshTokenRepository;
import dev.taskflow.identity.security.TokenClaims;
import dev.taskflow.identity.service.AuthService;
import dev.taskflow.identity.service.InviteService;
import dev.taskflow.identity.service.UserService;
import dev.taskflow.identity.web.ApiException;
import dev.taskflow.identity.web.Dtos.AcceptInviteRequest;
import dev.taskflow.identity.web.Dtos.CreateInviteRequest;
import dev.taskflow.identity.web.Dtos.InviteResponse;
import dev.taskflow.identity.web.Dtos.RegisterOrgRequest;
import dev.taskflow.identity.web.Dtos.UpdateUserRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InviteFlowTest extends IntegrationTest {

    @Autowired AuthService authService;
    @Autowired InviteService inviteService;
    @Autowired UserService userService;
    @Autowired AppUserRepository users;
    @Autowired InviteRepository invites;
    @Autowired OrganizationRepository organizations;
    @Autowired OutboxEventRepository outbox;
    @Autowired RefreshTokenRepository refreshTokens;

    private TokenClaims adminClaims;

    @BeforeEach
    void setUp() {
        outbox.deleteAll();
        invites.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
        organizations.deleteAll();

        authService.registerOrg(new RegisterOrgRequest(
                "Northwind Labs", "Asha Rao", "admin@taskflow.dev", "admin-pass"));

        UUID adminId = users.findByEmailIgnoreCase("admin@taskflow.dev")
                .orElseThrow().getId();
        UUID orgId = organizations.findAll().get(0).getId();
        adminClaims = new TokenClaims(adminId, orgId, Role.ADMIN, List.of());
    }

    @Test
    void invitingSomeoneQueuesAnEventInTheSameTransaction() {
        assertThat(outbox.count()).isZero();

        inviteService.create(adminClaims,
                new CreateInviteRequest("newdev@taskflow.dev", Role.MEMBER));

        // The outbox row is what makes the email durable. It commits with the
        // invite, so neither can exist without the other.
        assertThat(outbox.findAll())
                .hasSize(1)
                .allSatisfy(event -> {
                    assertThat(event.getEventType()).isEqualTo("user.invited");
                    assertThat(event.getPublishedAt()).isNull();
                    assertThat(event.getPayload()).contains("newdev@taskflow.dev");
                });
    }

    @Test
    void inviteTokenIsStoredHashedNotInPlaintext() {
        InviteResponse created = inviteService.create(adminClaims,
                new CreateInviteRequest("newdev@taskflow.dev", Role.MEMBER));

        String rawToken = created.acceptUrl().substring(
                created.acceptUrl().indexOf("token=") + 6);

        assertThat(invites.findAll())
                .singleElement()
                .satisfies(invite -> {
                    assertThat(invite.getTokenHash()).isNotEqualTo(rawToken);
                    assertThat(invite.getTokenHash()).hasSize(64);   // SHA-256 hex
                });
        assertThat(invites.findByTokenHash(rawToken)).isEmpty();
    }

    @Test
    void anInviteCannotBeAcceptedTwice() {
        InviteResponse created = inviteService.create(adminClaims,
                new CreateInviteRequest("newdev@taskflow.dev", Role.MEMBER));
        String token = created.acceptUrl().substring(
                created.acceptUrl().indexOf("token=") + 6);

        inviteService.accept(new AcceptInviteRequest(token, "New Dev", "member-pass"));

        assertThatThrownBy(() -> inviteService.accept(
                new AcceptInviteRequest(token, "Impostor", "another-pass")))
                .isInstanceOf(ApiException.class);

        assertThat(users.findAllByOrganizationIdOrderByNameAsc(adminClaims.orgId()))
                .hasSize(2);
    }

    @Test
    void membersCannotSendInvites() {
        TokenClaims memberClaims = new TokenClaims(
                UUID.randomUUID(), adminClaims.orgId(), Role.MEMBER, List.of());

        assertThatThrownBy(() -> inviteService.create(memberClaims,
                new CreateInviteRequest("newdev@taskflow.dev", Role.MEMBER)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCannotDemoteThemselves() {
        // Without this rule the last admin can lock everyone out of admin
        // functions, and the only fix is a manual UPDATE against the database.
        assertThatThrownBy(() -> userService.updateAsAdmin(
                adminClaims, adminClaims.userId(),
                new UpdateUserRequest(Role.MEMBER, null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
