package dev.taskflow.identity.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "taskflow.invite")
public record InviteProperties(
        Duration ttl,
        String acceptBaseUrl
) {
}
