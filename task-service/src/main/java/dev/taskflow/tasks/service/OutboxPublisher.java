package dev.taskflow.tasks.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.taskflow.tasks.domain.OutboxEvent;
import dev.taskflow.tasks.repo.OutboxEventRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class OutboxPublisher {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** Written in the caller's transaction, so the event and the state change commit together. */
    public void publish(String eventType, Map<String, Object> payload) {
        try {
            Map<String, Object> withType = new HashMap<>(payload);
            withType.put("eventType", eventType);
            repository.save(OutboxEvent.of(eventType, objectMapper.writeValueAsString(withType)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize outbox payload", e);
        }
    }
}
