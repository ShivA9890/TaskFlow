package dev.taskflow.identity;

import dev.taskflow.identity.domain.AppUser;
import dev.taskflow.identity.domain.UserStatus;
import dev.taskflow.identity.repo.AppUserRepository;
import dev.taskflow.identity.repo.OrganizationRepository;
import dev.taskflow.identity.repo.RefreshTokenRepository;
import dev.taskflow.identity.service.AuthService;
import dev.taskflow.identity.web.ApiException;
import dev.taskflow.identity.web.Dtos.LoginRequest;
import dev.taskflow.identity.web.Dtos.RegisterOrgRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthServiceTest extends IntegrationTest {

    @Autowired AuthService authService;
    @Autowired AppUserRepository users;
    @Autowired OrganizationRepository organizations;
    @Autowired RefreshTokenRepository refreshTokens;

    @BeforeEach
    void clean() {
        refreshTokens.deleteAll();
        users.deleteAll();
        organizations.deleteAll();
    }

    private void registerAdmin(String email, String password) {
        authService.registerOrg(new RegisterOrgRequest(
                "Northwind Labs", "Asha Rao", email, password));
    }

    @Test
    void wrongPasswordIsRejected() {
        registerAdmin("admin@taskflow.dev", "correct-horse");

        assertThatThrownBy(() ->
                authService.login(new LoginRequest("admin@taskflow.dev", "wrong")))
                .isInstanceOf(ApiException.class)
                .hasMessage("Email or password is incorrect.")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unknownEmailGivesTheSameMessageAsAWrongPassword() {
        registerAdmin("admin@taskflow.dev", "correct-horse");

        // Identical message on purpose: a different one would confirm which
        // addresses have accounts.
        assertThatThrownBy(() ->
                authService.login(new LoginRequest("nobody@taskflow.dev", "whatever")))
                .isInstanceOf(ApiException.class)
                .hasMessage("Email or password is incorrect.");
    }

    @Test
    void disabledAccountCannotSignIn() {
        registerAdmin("admin@taskflow.dev", "correct-horse");

        AppUser admin = users.findByEmailIgnoreCase("admin@taskflow.dev").orElseThrow();
        admin.setStatus(UserStatus.DISABLED);
        users.saveAndFlush(admin);

        assertThatThrownBy(() ->
                authService.login(new LoginRequest("admin@taskflow.dev", "correct-horse")))
                .isInstanceOf(ApiException.class)
                .hasMessage("This account is disabled.")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void emailCannotBeRegisteredTwiceRegardlessOfCase() {
        registerAdmin("admin@taskflow.dev", "correct-horse");

        assertThatThrownBy(() -> authService.registerOrg(new RegisterOrgRequest(
                "Another Org", "Someone Else", "ADMIN@TASKFLOW.DEV", "another-pass")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void passwordIsNeverStoredInPlaintext() {
        registerAdmin("admin@taskflow.dev", "correct-horse");

        AppUser admin = users.findByEmailIgnoreCase("admin@taskflow.dev").orElseThrow();
        assertThat(admin.getPasswordHash())
                .doesNotContain("correct-horse")
                .startsWith("$2");           // bcrypt prefix
    }
}
