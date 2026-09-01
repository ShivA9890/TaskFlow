package dev.taskflow.identity.web;

import dev.taskflow.identity.domain.AppUser;
import dev.taskflow.identity.domain.Invite;
import dev.taskflow.identity.domain.Role;
import dev.taskflow.identity.domain.Team;
import dev.taskflow.identity.domain.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class Dtos {

    private Dtos() {
    }

    // ---- requests ----

    public record RegisterOrgRequest(
            @NotBlank(message = "Give the workspace a name.") String orgName,
            @NotBlank(message = "Enter your name.") String name,
            @Email(message = "Enter a valid email address.")
            @NotBlank(message = "Enter your email.") String email,
            @Size(min = 8, message = "Use at least 8 characters for the password.")
            String password) {
    }

    public record LoginRequest(
            @NotBlank(message = "Enter your email.") String email,
            @NotBlank(message = "Enter your password.") String password) {
    }

    public record RefreshRequest(
            @NotBlank(message = "A refresh token is required.") String refreshToken) {
    }

    public record UpdateMeRequest(
            String name,
            String timezone,
            String password) {
    }

    public record UpdateUserRequest(
            Role role,
            UserStatus status) {
    }

    public record CreateInviteRequest(
            @Email(message = "Enter a valid email address.")
            @NotBlank(message = "Enter an email address.") String email,
            @NotNull(message = "Choose a role.") Role role) {
    }

    public record AcceptInviteRequest(
            @NotBlank(message = "This invite link is missing its token.") String token,
            @NotBlank(message = "Enter your name.") String name,
            @Size(min = 8, message = "Use at least 8 characters for the password.")
            String password) {
    }

    public record CreateTeamRequest(
            @NotBlank(message = "Give the team a name.") String name) {
    }

    public record AddTeamMemberRequest(
            @NotNull(message = "Choose someone to add.") UUID userId) {
    }

    // ---- responses ----

    public record AuthResponse(String accessToken, String refreshToken) {
    }

    public record UserResponse(
            UUID id,
            UUID orgId,
            String email,
            String name,
            Role role,
            UserStatus status,
            String timezone) {

        public static UserResponse from(AppUser user) {
            return new UserResponse(
                    user.getId(),
                    user.getOrganization().getId(),
                    user.getEmail(),
                    user.getName(),
                    user.getRole(),
                    user.getStatus(),
                    user.getTimezone());
        }
    }

    public record TeamResponse(UUID id, String name, List<UUID> memberIds) {

        public static TeamResponse from(Team team) {
            return new TeamResponse(team.getId(), team.getName(),
                    List.copyOf(team.getMemberIds()));
        }
    }

    /**
     * acceptUrl is populated only on creation. We store a hash of the token, so
     * listing invites cannot reproduce the link — by design.
     */
    public record InviteResponse(
            UUID id,
            String email,
            Role role,
            Instant expiresAt,
            Instant acceptedAt,
            String acceptUrl) {

        public static InviteResponse from(Invite invite, String acceptUrl) {
            return new InviteResponse(
                    invite.getId(),
                    invite.getEmail(),
                    invite.getRole(),
                    invite.getExpiresAt(),
                    invite.getAcceptedAt(),
                    acceptUrl);
        }

        public static InviteResponse from(Invite invite) {
            return from(invite, null);
        }
    }
}
