package dev.taskflow.tasks.web;

import dev.taskflow.tasks.security.CurrentUser;
import dev.taskflow.tasks.service.TaskCommandService;
import dev.taskflow.tasks.service.TaskQueryService;
import dev.taskflow.tasks.web.Dtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class TaskController {

    private final TaskCommandService commands;
    private final TaskQueryService queries;

    public TaskController(TaskCommandService commands, TaskQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @GetMapping("/tasks")
    public List<TaskResponse> query(@RequestParam(required = false) UUID boardId,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String assignee) {
        return queries.query(CurrentUser.require(), boardId, status, assignee);
    }

    @PostMapping("/boards/{boardId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@PathVariable UUID boardId,
                               @Valid @RequestBody CreateTaskRequest request) {
        var claims = CurrentUser.requireAdmin("Only admins can create tasks.");
        return commands.create(claims, boardId, request);
    }

    /** Members reach this too — the service rejects anything beyond columnId. */
    @PatchMapping("/tasks/{id}")
    public TaskResponse update(@PathVariable UUID id,
                               @Valid @RequestBody UpdateTaskRequest request) {
        return commands.update(CurrentUser.require(), id, request);
    }

    @PostMapping("/tasks/{id}/move")
    public TaskResponse move(@PathVariable UUID id,
                             @Valid @RequestBody MoveTaskRequest request) {
        return commands.move(CurrentUser.require(), id, request);
    }

    @DeleteMapping("/tasks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        commands.delete(CurrentUser.requireAdmin("Only admins can delete tasks."), id);
    }
}
