package dev.taskflow.tasks.web;

import dev.taskflow.tasks.domain.Board;
import dev.taskflow.tasks.domain.BoardColumn;
import dev.taskflow.tasks.domain.Severity;
import dev.taskflow.tasks.domain.Task;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class Dtos {

    private Dtos() {
    }

    // ---- requests ----

    public record CreateBoardRequest(
            @NotBlank(message = "Give the board a name.") String name,
            UUID teamId,
            @NotNull(message = "A board needs columns.")
            @Size(min = 3, max = 6, message = "A board needs between 3 and 6 columns.")
            List<@NotBlank(message = "Every column needs a name.") String> columns) {
    }

    public record UpdateBoardRequest(String name, UUID teamId) {
    }

    public record CreateColumnRequest(
            @NotBlank(message = "Give the column a name.")
            @Size(max = 60, message = "Keep the column name under 60 characters.")
            String name) {
    }

    public record CreateTaskRequest(
            @NotBlank(message = "Give the task a title.")
            @Size(max = 200, message = "Keep the title under 200 characters.")
            String title,
            String body,
            UUID assigneeId,
            @NotNull(message = "Choose a severity.") Severity severity,
            Instant dueDate,
            @NotNull(message = "Choose a column.") UUID columnId) {
    }

    /** Every field optional. Members may only send columnId. */
    public record UpdateTaskRequest(
            String title,
            String body,
            UUID assigneeId,
            Severity severity,
            Instant dueDate,
            UUID columnId) {
    }

    public record MoveTaskRequest(
            @NotNull(message = "Choose a column.") UUID columnId,
            /** Index within the target column. Null appends to the end. */
            Integer index) {
    }

    // ---- responses ----

    public record BoardResponse(UUID id, String name, UUID teamId) {

        public static BoardResponse from(Board board) {
            return new BoardResponse(board.getId(), board.getName(), board.getTeamId());
        }
    }

    public record ColumnResponse(
            UUID id, UUID boardId, String name, int position, boolean isTerminal) {

        public static ColumnResponse from(BoardColumn column) {
            return new ColumnResponse(
                    column.getId(),
                    column.getBoard().getId(),
                    column.getName(),
                    column.getPosition(),
                    column.isTerminal());
        }
    }

    public record TaskResponse(
            UUID id,
            UUID boardId,
            UUID columnId,
            String title,
            String body,
            UUID assigneeId,
            UUID reporterId,
            Severity severity,
            Instant dueDate,
            double position,
            Instant movedAt,
            Instant completedAt,
            Instant createdAt) {

        public static TaskResponse from(Task task) {
            return new TaskResponse(
                    task.getId(),
                    task.getBoard().getId(),
                    task.getColumn().getId(),
                    task.getTitle(),
                    task.getBody(),
                    task.getAssigneeId(),
                    task.getReporterId(),
                    task.getSeverity(),
                    task.getDueDate(),
                    task.getPosition(),
                    task.getMovedAt(),
                    task.getCompletedAt(),
                    task.getCreatedAt());
        }
    }

    public record BoardDetailResponse(
            UUID id,
            String name,
            UUID teamId,
            List<ColumnResponse> columns,
            List<TaskResponse> tasks) {
    }

    /** Shape the reminder job consumes. Includes what the email needs. */
    public record StalledTaskResponse(
            UUID id,
            UUID orgId,
            String title,
            UUID assigneeId,
            Severity severity,
            Instant dueDate) {

        public static StalledTaskResponse from(Task task) {
            return new StalledTaskResponse(
                    task.getId(),
                    task.getOrgId(),
                    task.getTitle(),
                    task.getAssigneeId(),
                    task.getSeverity(),
                    task.getDueDate());
        }
    }
}
