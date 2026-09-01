package dev.taskflow.tasks.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tasks")
@Getter
@Setter
public class Task {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "board_id", nullable = false, updatable = false)
    private Board board;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "column_id", nullable = false)
    private BoardColumn column;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body = "";

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "reporter_id", nullable = false, updatable = false)
    private UUID reporterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Severity severity;

    @Column(name = "due_date")
    private Instant dueDate;

    @Column(nullable = false)
    private double position;

    @Column(name = "moved_at", nullable = false)
    private Instant movedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Guards against two admins editing the same task from stale reads. */
    @Version
    private long version;

    public static Task create(Board board, BoardColumn column, String title, String body,
                              UUID assigneeId, UUID reporterId, Severity severity,
                              Instant dueDate, double position) {
        Instant now = Instant.now();
        Task task = new Task();
        task.id = UUID.randomUUID();
        task.orgId = board.getOrgId();
        task.board = board;
        task.column = column;
        task.title = title;
        task.body = body == null ? "" : body;
        task.assigneeId = assigneeId;
        task.reporterId = reporterId;
        task.severity = severity;
        task.dueDate = dueDate;
        task.position = position;
        task.movedAt = now;
        task.completedAt = column.isTerminal() ? now : null;
        task.createdAt = now;
        task.updatedAt = now;
        return task;
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }
}
