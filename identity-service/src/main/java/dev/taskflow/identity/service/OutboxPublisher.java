package dev.taskflow.identity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.taskflow.identity.domain.OutboxEvent;
import dev.taskflow.identity.repo.OutboxEventRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Writes an event into the same transaction as the state change that caused it.
 * A separate poller ships these to SNS. Nothing here talks to AWS.
 */
@Service
public class OutboxPublisher {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void publish(String eventType, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            repository.save(OutboxEvent.of(eventType, json));
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize outbox payload", e);
        }
    }
}
