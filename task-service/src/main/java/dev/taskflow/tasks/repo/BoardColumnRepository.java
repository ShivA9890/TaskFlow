package dev.taskflow.tasks.repo;

import dev.taskflow.tasks.domain.BoardColumn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BoardColumnRepository extends JpaRepository<BoardColumn, UUID> {

    List<BoardColumn> findAllByBoardIdOrderByPositionAsc(UUID boardId);

    Optional<BoardColumn> findByIdAndBoardId(UUID id, UUID boardId);

    long countByBoardId(UUID boardId);
}
