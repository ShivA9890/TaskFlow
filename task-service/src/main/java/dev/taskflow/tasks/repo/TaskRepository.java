package dev.taskflow.tasks.repo;

import dev.taskflow.tasks.domain.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findAllByBoardIdOrderByPositionAsc(UUID boardId);

    Optional<Task> findByIdAndOrgId(UUID id, UUID orgId);

    List<Task> findAllByOrgIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(UUID orgId);

    List<Task> findAllByOrgIdAndAssigneeIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
            UUID orgId, UUID assigneeId);

    @Query("select coalesce(max(t.position), 0) from Task t where t.column.id = :columnId")
    double maxPositionInColumn(UUID columnId);

    long countByColumnId(UUID columnId);

    /**
     * Candidates for the "due soon, no progress" reminder: open, assigned, dated,
     * inside the window, and still sitting in the board's first column.
     */
    @Query("""
            select t from Task t
            where t.completedAt is null
              and t.assigneeId is not null
              and t.dueDate is not null
              and t.dueDate <= :cutoff
              and t.column.position = 0
            order by t.dueDate asc
            """)
    List<Task> findStalled(Instant cutoff);
}
