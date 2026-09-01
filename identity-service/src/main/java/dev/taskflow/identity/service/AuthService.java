// package dev.taskflow.identity.service;

// import dev.taskflow.identity.domain.*;
// import dev.taskflow.identity.repo.*;
// import dev.taskflow.identity.security.JwtService;
// import dev.taskflow.identity.security.TokenClaims;
// import dev.taskflow.identity.security.Tokens;
// import dev.taskflow.identity.web.ApiException;
// import dev.taskflow.identity.web.Dtos.*;
// import org.springframework.security.crypto.password.PasswordEncoder;
// import org.springframework.stereotype.Service;
// import org.springframework.transaction.annotation.Transactional;

// import java.time.Instant;
// import java.util.List;
// import java.util.UUID;

// @Service
// public class AuthService {

//     private final OrganizationRepository organizations;
//     private final AppUserRepository users;
//     private final TeamRepository teams;
//     private final RefreshTokenRepository refreshTokens;
//     private final PasswordEncoder passwordEncoder;
//     private final JwtService jwtService;

//     public AuthService(OrganizationRepository organizations,
//                        AppUserRepository users,
//                        TeamRepository teams,
//                        RefreshTokenRepository refreshTokens,
//                        PasswordEncoder passwordEncoder,
//                        JwtService jwtService) {
//         this.organizations = organizations;
//         this.users = users;
//         this.teams = teams;
//         this.refreshTokens = refreshTokens;
//         this.passwordEncoder = passwordEncoder;
//         this.jwtService = jwtService;
//     }

//     @Transactional
//     public AuthResponse registerOrg(RegisterOrgRequest request) {
//         if (users.existsByEmailIgnoreCase(request.email())) {
//             throw ApiException.conflict("That email already has an account.");
//         }
//         Organization org = organizations.save(Organization.of(request.orgName()));
//         AppUser admin = users.save(AppUser.create(
//                 org,
//                 request.email(),
//                 passwordEncoder.encode(request.password()),
//                 request.name(),
//                 Role.ADMIN,
//                 "UTC"));
//         return issueTokens(admin);
//     }

//     @Transactional
//     public AuthResponse login(LoginRequest request) {
//         AppUser user = users.findByEmailIgnoreCase(request.email())
//                 // Same message for unknown email and wrong password: don't confirm
//                 // which addresses have accounts.
//                 .orElseThrow(() -> ApiException.unauthorized("Email or password is incorrect."));

//         if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
//             throw ApiException.unauthorized("Email or password is incorrect.");
//         }
//         if (user.getStatus() != UserStatus.ACTIVE) {
//             throw ApiException.forbidden("This account is disabled.");
//         }
//         return issueTokens(user);
//     }

//     @Transactional
//     public AuthResponse refresh(RefreshRequest request) {
//         RefreshToken stored = refreshTokens.findByTokenHash(Tokens.hash(request.refreshToken()))
//                 .orElseThrow(() -> ApiException.unauthorized("Sign in again to continue."));

//         if (!stored.isUsable()) {
//             // A replayed or expired token invalidates the whole family.
//             refreshTokens.revokeAllForUser(stored.getUser().getId());
//             throw ApiException.unauthorized("Sign in again to continue.");
//         }
//         stored.setRevoked(true);
//         return issueTokens(stored.getUser());
//     }

//     @Transactional
//     public void logout(TokenClaims claims) {
//         refreshTokens.revokeAllForUser(claims.userId());
//     }

//     private AuthResponse issueTokens(AppUser user) {
//         List<UUID> teamIds = teams
//                 .findAllForMember(user.getOrganization().getId(), user.getId())
//                 .stream()
//                 .map(Team::getId)
//                 .toList();

//         String accessToken = jwtService.issueAccessToken(user, teamIds);

//         String rawRefresh = Tokens.generate();
//         refreshTokens.save(RefreshToken.issue(
//                 user,
//                 Tokens.hash(rawRefresh),
//                 Instant.now().plus(jwtService.refreshTokenTtl())));

//         return new AuthResponse(accessToken, rawRefresh);
//     }
// }


package dev.taskflow.identity.service;

import dev.taskflow.identity.domain.*;
import dev.taskflow.identity.repo.*;
import dev.taskflow.identity.security.TokenClaims;
import dev.taskflow.identity.security.Tokens;
import dev.taskflow.identity.web.ApiException;
import dev.taskflow.identity.web.Dtos.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final OrganizationRepository organizations;
    private final AppUserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final AuthServiceSupport support;

    public AuthService(OrganizationRepository organizations,
                       AppUserRepository users,
                       RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder,
                       AuthServiceSupport support) {
        this.organizations = organizations;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.support = support;
    }

    @Transactional
    public AuthResponse registerOrg(RegisterOrgRequest request) {
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw ApiException.conflict("That email already has an account.");
        }
        Organization org = organizations.save(Organization.of(request.orgName()));
        AppUser admin = users.save(AppUser.create(
                org,
                request.email(),
                passwordEncoder.encode(request.password()),
                request.name(),
                Role.ADMIN,
                "UTC"));
        return support.issueTokens(admin);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        AppUser user = users.findByEmailIgnoreCase(request.email())
                // Same message for unknown email and wrong password: don't confirm
                // which addresses have accounts.
                .orElseThrow(() -> ApiException.unauthorized("Email or password is incorrect."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Email or password is incorrect.");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.forbidden("This account is disabled.");
        }
        return support.issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        RefreshToken stored = refreshTokens.findByTokenHash(Tokens.hash(request.refreshToken()))
                .orElseThrow(() -> ApiException.unauthorized("Sign in again to continue."));

        if (!stored.isUsable()) {
            // A replayed or expired token invalidates the whole family.
            refreshTokens.revokeAllForUser(stored.getUser().getId());
            throw ApiException.unauthorized("Sign in again to continue.");
        }
        stored.setRevoked(true);
        return support.issueTokens(stored.getUser());
    }

    @Transactional
    public void logout(TokenClaims claims) {
        refreshTokens.revokeAllForUser(claims.userId());
    }
}