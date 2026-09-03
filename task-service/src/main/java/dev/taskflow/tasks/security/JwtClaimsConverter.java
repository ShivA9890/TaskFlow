package dev.taskflow.tasks.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns the validated JWT into our own principal. Spring has already checked the
 * signature against the JWKS and the issuer by the time this runs.
 */
@Component
public class JwtClaimsConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String role = Optional.ofNullable(jwt.getClaimAsString("role")).orElse("MEMBER");

        List<UUID> teamIds = Optional
                .ofNullable(jwt.getClaimAsStringList("teamIds"))
                .orElseGet(List::of)
                .stream()
                .map(UUID::fromString)
                .toList();

        TokenClaims claims = new TokenClaims(
                UUID.fromString(jwt.getSubject()),
                UUID.fromString(jwt.getClaimAsString("orgId")),
                role,
                teamIds);

        return new UsernamePasswordAuthenticationToken(
                claims,
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

}
