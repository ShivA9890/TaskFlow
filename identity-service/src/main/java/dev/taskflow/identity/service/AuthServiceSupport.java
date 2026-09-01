package dev.taskflow.identity.service;

import dev.taskflow.identity.domain.AppUser;
import dev.taskflow.identity.domain.RefreshToken;
import dev.taskflow.identity.domain.Team;
import dev.taskflow.identity.repo.RefreshTokenRepository;
import dev.taskflow.identity.repo.TeamRepository;
import dev.taskflow.identity.security.JwtService;
import dev.taskflow.identity.security.Tokens;
import dev.taskflow.identity.web.Dtos.AuthResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Token issuing shared by AuthService and InviteService. Kept separate so the two
 * do not depend on each other, which would be a circular bean reference.
 */
@Service
public class AuthServiceSupport {

    private final TeamRepository teams;
    private final RefreshTokenRepository refreshTokens;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthServiceSupport(TeamRepository teams,
                              RefreshTokenRepository refreshTokens,
                              JwtService jwtService,
                              PasswordEncoder passwordEncoder) {
        this.teams = teams;
        this.refreshTokens = refreshTokens;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    public AuthResponse issueTokens(AppUser user) {
        List<UUID> teamIds = teams
                .findAllForMember(user.getOrganization().getId(), user.getId())
                .stream()
                .map(Team::getId)
                .toList();

        String accessToken = jwtService.issueAccessToken(user, teamIds);

        String rawRefresh = Tokens.generate();
        refreshTokens.save(RefreshToken.issue(
                user,
                Tokens.hash(rawRefresh),
                Instant.now().plus(jwtService.refreshTokenTtl())));

        return new AuthResponse(accessToken, rawRefresh);
    }
}
