package dev.taskflow.tasks.service;

import dev.taskflow.tasks.domain.Task;
import dev.taskflow.tasks.repo.TaskRepository;
import dev.taskflow.tasks.security.TokenClaims;
import dev.taskflow.tasks.web.Dtos.StalledTaskResponse;
import dev.taskflow.tasks.web.Dtos.TaskResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TaskQueryService {

    private final TaskRepository tasks;
    private final Duration stallWindow;

    public TaskQueryService(TaskRepository tasks,
                            @Value("${taskflow.reminder.stall-window}") Duration stallWindow) {
        this.tasks = tasks;
        this.stallWindow = stallWindow;
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> query(TokenClaims claims, UUID boardId,
                                    String status, String assignee) {
        boolean completedOnly = "completed".equalsIgnoreCase(status);
        boolean mineOnly = "me".equalsIgnoreCase(assignee);

        List<Task> result;
        if (completedOnly && mineOnly) {
            result = tasks
                    .findAllByOrgIdAndAssigneeIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                            claims.orgId(), claims.userId());
        } else if (completedOnly) {
            result = tasks.findAllByOrgIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                    claims.orgId());
        } else if (boardId != null) {
            result = tasks.findAllByBoardIdOrderByPositionAsc(boardId).stream()
                    .filter(t -> t.getOrgId().equals(claims.orgId()))
                    .toList();
        } else {
            result = List.of();
        }

        if (mineOnly && !completedOnly) {
            result = result.stream()
                    .filter(t -> claims.userId().equals(t.getAssigneeId()))
                    .toList();
        }
        if (boardId != null && completedOnly) {
            result = result.stream()
                    .filter(t -> t.getBoard().getId().equals(boardId))
                    .toList();
        }
        return result.stream().map(TaskResponse::from).toList();
    }

    /** Called by the reminder job, across all orgs. Not reachable with a user token. */
    @Transactional(readOnly = true)
    public List<StalledTaskResponse> stalled() {
        return tasks.findStalled(Instant.now().plus(stallWindow))
                .stream()
                .map(StalledTaskResponse::from)
                .toList();
    }
}
