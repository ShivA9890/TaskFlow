package dev.taskflow.tasks.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "taskflow.internal")
public record InternalProperties(String serviceToken) {
}
