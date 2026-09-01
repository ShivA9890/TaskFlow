package dev.taskflow.identity.repo;

import dev.taskflow.identity.domain.OutboxEvent;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findAllByPublishedAtIsNullOrderByCreatedAtAsc(Limit limit);
}
