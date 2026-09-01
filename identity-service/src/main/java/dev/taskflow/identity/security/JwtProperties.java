package dev.taskflow.identity.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.time.Duration;

@ConfigurationProperties(prefix = "taskflow.jwt")
public record JwtProperties(
        Resource privateKeyLocation,
        String keyId,
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl
) {
}
