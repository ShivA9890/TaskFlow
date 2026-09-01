
package dev.taskflow.identity.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.taskflow.identity.domain.AppUser;
import dev.taskflow.identity.domain.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final JwtProperties props;
    private final RSASSASigner signer;
    private final RSASSAVerifier verifier;
    private final JWKSet jwkSet;

    public JwtService(JwtProperties props) throws Exception {
        this.props = props;

        RSAPrivateKey privateKey = loadPrivateKey(props);
        RSAPublicKey publicKey = derivePublicKey(privateKey);

        this.signer = new RSASSASigner(privateKey);
        this.verifier = new RSASSAVerifier(publicKey);
        this.jwkSet = new JWKSet(new RSAKey.Builder(publicKey)
                .keyID(props.keyId())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build());

        log.info("JWT signing key loaded, kid={}", props.keyId());
    }

    private static RSAPrivateKey loadPrivateKey(JwtProperties props) throws Exception {
        String pem;
        try (InputStream in = props.privateKeyLocation().getInputStream()) {
            pem = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String base64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    /** The public half is recoverable from a PKCS#8 RSA key, so we only ship one file. */
    private static RSAPublicKey derivePublicKey(RSAPrivateKey privateKey) throws Exception {
        if (!(privateKey instanceof RSAPrivateCrtKey crt)) {
            throw new IllegalStateException("Private key must be an RSA CRT key");
        }
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
    }

    public String issueAccessToken(AppUser user, List<UUID> teamIds) {
        Instant now = Instant.now();
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(props.issuer())
                    .subject(user.getId().toString())
                    .jwtID(UUID.randomUUID().toString())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(props.accessTokenTtl())))
                    .claim("orgId", user.getOrganization().getId().toString())
                    .claim("role", user.getRole().name())
                    .claim("name", user.getName())
                    .claim("email", user.getEmail())
                    .claim("teamIds", teamIds.stream().map(UUID::toString).toList())
                    .build();

            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .type(JOSEObjectType.JWT)
                            .keyID(props.keyId())
                            .build(),
                    claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign access token", e);
        }
    }

    public Optional<TokenClaims> verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(verifier)) {
                return Optional.empty();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (claims.getExpirationTime() == null
                    || claims.getExpirationTime().toInstant().isBefore(Instant.now())) {
                return Optional.empty();
            }
            if (!props.issuer().equals(claims.getIssuer())) {
                return Optional.empty();
            }
            List<UUID> teamIds = Optional
                    .ofNullable(claims.getStringListClaim("teamIds"))
                    .orElseGet(List::of)
                    .stream()
                    .map(UUID::fromString)
                    .toList();

            return Optional.of(new TokenClaims(
                    UUID.fromString(claims.getSubject()),
                    UUID.fromString(claims.getStringClaim("orgId")),
                    Role.valueOf(claims.getStringClaim("role")),
                    teamIds));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Served at /api/v1/auth/.well-known/jwks.json for the other services to fetch. */
    public String jwksJson() {
        return jwkSet.toPublicJWKSet().toString();
    }

    public java.time.Duration refreshTokenTtl() {
        return props.refreshTokenTtl();
    }

    public java.time.Duration accessTokenTtl() {
        return props.accessTokenTtl();
    }
}
