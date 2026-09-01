package dev.taskflow.identity.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox")
@Getter
@Setter
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    public static OutboxEvent of(String eventType, String payloadJson) {
        OutboxEvent event = new OutboxEvent();
        event.id = UUID.randomUUID();
        event.eventType = eventType;
        event.payload = payloadJson;
        event.createdAt = Instant.now();
        return event;
    }
}
