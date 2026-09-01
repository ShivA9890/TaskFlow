package dev.taskflow.tasks.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "boards")
@Getter
@Setter
public class Board {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "team_id")
    private UUID teamId;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static Board create(UUID orgId, UUID teamId, String name, UUID createdBy) {
        Board board = new Board();
        board.id = UUID.randomUUID();
        board.orgId = orgId;
        board.teamId = teamId;
        board.name = name;
        board.createdBy = createdBy;
        board.createdAt = Instant.now();
        return board;
    }
}
