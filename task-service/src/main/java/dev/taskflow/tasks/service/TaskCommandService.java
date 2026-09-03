package dev.taskflow.tasks.service;

import dev.taskflow.tasks.domain.Board;
import dev.taskflow.tasks.domain.BoardColumn;
import dev.taskflow.tasks.domain.Task;
import dev.taskflow.tasks.repo.BoardColumnRepository;
import dev.taskflow.tasks.repo.BoardRepository;
import dev.taskflow.tasks.repo.TaskRepository;
import dev.taskflow.tasks.security.TokenClaims;
import dev.taskflow.tasks.web.ApiException;
import dev.taskflow.tasks.web.Dtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TaskCommandService {

    private static final double POSITION_GAP = 1024.0;

    private final TaskRepository tasks;
    private final BoardRepository boards;
    private final BoardColumnRepository columns;
    private final BoardService boardService;
    private final OutboxPublisher outbox;

    public TaskCommandService(TaskRepository tasks, BoardRepository boards,
                              BoardColumnRepository columns, BoardService boardService,
                              OutboxPublisher outbox) {
        this.tasks = tasks;
        this.boards = boards;
        this.columns = columns;
        this.boardService = boardService;
        this.outbox = outbox;
    }

    @Transactional
    public TaskResponse create(TokenClaims claims, UUID boardId, CreateTaskRequest request) {
        if(!claims.isAdmin()){
            throw ApiException.forbidden("Only admin can create tasks");
        }
        Board board = boardService.requireBoard(claims, boardId);
        BoardColumn column = requireColumn(boardId, request.columnId());

        double position = tasks.maxPositionInColumn(column.getId()) + POSITION_GAP;

        Task task = tasks.save(Task.create(
                board, column, request.title().trim(), request.body(),
                request.assigneeId(), claims.userId(), request.severity(),
                request.dueDate(), position));

        if (task.getAssigneeId() != null) {
            publishAssigned(task);
        }
        if (column.isTerminal()) {
            publishCompleted(task, claims.userId());
        }
        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse update(TokenClaims claims, UUID taskId, UpdateTaskRequest request) {
        Task task = requireTask(claims, taskId);

        // The member rule, enforced here rather than in the UI.
        if (!claims.isAdmin() && touchesMoreThanColumn(request)) {
            throw ApiException.forbidden(
                    "Members can move tasks between columns, but cannot edit task details.");
        }

        UUID previousAssignee = task.getAssigneeId();

        if (request.title() != null) {
            if (request.title().isBlank()) {
                throw ApiException.unprocessable("Give the task a title.");
            }
            task.setTitle(request.title().trim());
        }
        if (request.body() != null) {
            task.setBody(request.body());
        }
        if (request.severity() != null) {
            task.setSeverity(request.severity());
        }
        if (request.dueDate() != null) {
            task.setDueDate(request.dueDate());
        }
        if (request.assigneeId() != null) {
            task.setAssigneeId(request.assigneeId());
        }
        if (request.columnId() != null) {
            applyColumnChange(claims, task, request.columnId(), null);
        }

        if (request.assigneeId() != null && !request.assigneeId().equals(previousAssignee)) {
            publishAssigned(task);
        }
        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse move(TokenClaims claims, UUID taskId, MoveTaskRequest request) {
        Task task = requireTask(claims, taskId);
        applyColumnChange(claims, task, request.columnId(), request.index());
        return TaskResponse.from(task);
    }

    @Transactional
    public void delete(TokenClaims claims, UUID taskId) {
        tasks.delete(requireTask(claims, taskId));
    }

    /**
     * Locks the board row first, so concurrent drops onto the same column cannot
     * both read the same neighbours and compute the same midpoint.
     */
    private void applyColumnChange(TokenClaims claims, Task task, UUID columnId, Integer index) {
        UUID boardId = task.getBoard().getId();
        boards.lockByIdAndOrgId(boardId, claims.orgId())
                .orElseThrow(() -> ApiException.notFound("That board no longer exists."));

        BoardColumn target = requireColumn(boardId, columnId);
        boolean sameColumn = target.getId().equals(task.getColumn().getId());

        if (!sameColumn) {
            task.setColumn(target);
            task.setMovedAt(Instant.now());
        }
        task.setPosition(positionFor(target.getId(), task.getId(), index));

        boolean wasCompleted = task.isCompleted();
        if (target.isTerminal()) {
            if (!wasCompleted) {
                task.setCompletedAt(Instant.now());
                publishCompleted(task, claims.userId());
            }
        } else {
            task.setCompletedAt(null);
        }
    }

    /** Midpoint between the neighbours at the requested index. Null index appends. */
    private double positionFor(UUID columnId, UUID movingTaskId, Integer index) {
        if (index == null) {
            return tasks.maxPositionInColumn(columnId) + POSITION_GAP;
        }
        List<Task> siblings = tasks.findAllByBoardIdOrderByPositionAsc(
                        columns.findById(columnId).orElseThrow().getBoard().getId())
                .stream()
                .filter(t -> t.getColumn().getId().equals(columnId))
                .filter(t -> !t.getId().equals(movingTaskId))
                .toList();

        int clamped = Math.max(0, Math.min(index, siblings.size()));

        double before = clamped == 0 ? 0.0 : siblings.get(clamped - 1).getPosition();
        double after = clamped == siblings.size()
                ? before + 2 * POSITION_GAP
                : siblings.get(clamped).getPosition();

        return (before + after) / 2.0;
    }

    private static boolean touchesMoreThanColumn(UpdateTaskRequest r) {
        return r.title() != null || r.body() != null || r.assigneeId() != null
                || r.severity() != null || r.dueDate() != null;
    }

    private void publishAssigned(Task task) {
        outbox.publish("task.assigned", basePayload(task));
    }

    private void publishCompleted(Task task, UUID actorId) {
        Map<String, Object> payload = basePayload(task);
        payload.put("completedBy", actorId.toString());
        outbox.publish("task.completed", payload);
    }

    private Map<String, Object> basePayload(Task task) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", task.getId().toString());
        payload.put("orgId", task.getOrgId().toString());
        payload.put("boardId", task.getBoard().getId().toString());
        payload.put("title", task.getTitle());
        payload.put("severity", task.getSeverity().name());
        payload.put("assigneeId",
                task.getAssigneeId() == null ? null : task.getAssigneeId().toString());
        payload.put("dueDate", task.getDueDate() == null ? null : task.getDueDate().toString());
        return payload;
    }

    private Task requireTask(TokenClaims claims, UUID taskId) {
        return tasks.findByIdAndOrgId(taskId, claims.orgId())
                .orElseThrow(() -> ApiException.notFound("That task no longer exists."));
    }

    private BoardColumn requireColumn(UUID boardId, UUID columnId) {
        return columns.findByIdAndBoardId(columnId, boardId)
                .orElseThrow(() -> ApiException.notFound("That column no longer exists."));
    }
}
