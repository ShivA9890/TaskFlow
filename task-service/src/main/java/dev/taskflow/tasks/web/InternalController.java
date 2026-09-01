package dev.taskflow.tasks.web;

import dev.taskflow.tasks.service.TaskQueryService;
import dev.taskflow.tasks.web.Dtos.StalledTaskResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Service-to-service only, guarded by X-Internal-Token. Keeps database credentials
 * in one place: the reminder Lambda asks task-service instead of querying RDS.
 */
@RestController
@RequestMapping("/api/v1/internal")
public class InternalController {

    private final TaskQueryService queries;

    public InternalController(TaskQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/tasks/stalled")
    public List<StalledTaskResponse> stalled() {
        return queries.stalled();
    }
}
