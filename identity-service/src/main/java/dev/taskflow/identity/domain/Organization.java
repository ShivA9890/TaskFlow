package dev.taskflow.identity.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organizations")
@Getter
@Setter
public class Organization {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static Organization of(String name) {
        Organization org = new Organization();
        org.id = UUID.randomUUID();
        org.name = name;
        org.createdAt = Instant.now();
        return org;
    }
}
