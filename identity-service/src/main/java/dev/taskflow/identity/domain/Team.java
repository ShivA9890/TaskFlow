package dev.taskflow.identity.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "teams")
@Getter
@Setter
public class Team {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_id", nullable = false)
    private Organization organization;

    @Column(nullable = false)
    private String name;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "team_members", joinColumns = @JoinColumn(name = "team_id"))
    @Column(name = "user_id", nullable = false)
    private Set<UUID> memberIds = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static Team of(Organization org, String name, UUID creatorId) {
        Team team = new Team();
        team.id = UUID.randomUUID();
        team.organization = org;
        team.name = name;
        team.memberIds.add(creatorId);
        team.createdAt = Instant.now();
        return team;
    }
}
