package dev.taskflow.tasks;

import dev.taskflow.tasks.domain.Severity;
import dev.taskflow.tasks.repo.BoardRepository;
import dev.taskflow.tasks.repo.OutboxEventRepository;
import dev.taskflow.tasks.repo.TaskRepository;
import dev.taskflow.tasks.security.TokenClaims;
import dev.taskflow.tasks.service.BoardService;
import dev.taskflow.tasks.service.TaskCommandService;
import dev.taskflow.tasks.web.ApiException;
import dev.taskflow.tasks.web.Dtos.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskPermissionTest extends IntegrationTest {

    @Autowired BoardService boardService;
    @Autowired TaskCommandService commands;
    @Autowired BoardRepository boards;
    @Autowired TaskRepository tasks;
    @Autowired OutboxEventRepository outbox;

    private UUID orgId;
    private TokenClaims admin;
    private TokenClaims member;
    private UUID memberId;
    private BoardDetailResponse board;

    @BeforeEach
    void setUp() {
        outbox.deleteAll();
        tasks.deleteAll();
        boards.deleteAll();

        orgId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        admin = new TokenClaims(UUID.randomUUID(), orgId, "ADMIN", List.of());
        member = new TokenClaims(memberId, orgId, "MEMBER", List.of());

        BoardResponse created = boardService.create(admin, new CreateBoardRequest(
                "Platform", null, List.of("To do", "In progress", "Completed")));
        board = boardService.detail(admin, created.id());
    }

    private TaskResponse createTask() {
        return commands.create(admin, board.id(), new CreateTaskRequest(
                "Rotate RDS credentials",
                "Move the master secret into Secrets Manager.",
                memberId,
                Severity.CRITICAL,
                Instant.now().plus(30, ChronoUnit.HOURS),
                board.columns().get(0).id()));
    }

    @Test
    void assigningATaskQueuesAnEvent() {
        createTask();

        assertThat(outbox.findAll())
                .singleElement()
                .satisfies(e -> assertThat(e.getEventType()).isEqualTo("task.assigned"));
    }

    @Test
    void aMemberMayMoveATaskBetweenColumns() {
        TaskResponse task = createTask();
        UUID inProgress = board.columns().get(1).id();

        TaskResponse moved = commands.move(member, task.id(),
                new MoveTaskRequest(inProgress, null));

        assertThat(moved.columnId()).isEqualTo(inProgress);
        assertThat(moved.completedAt()).isNull();
    }

    @Test
    void aMemberMayNotEditTaskDetails() {
        TaskResponse task = createTask();

        assertThatThrownBy(() -> commands.update(member, task.id(),
                new UpdateTaskRequest("Renamed by a member", null, null, null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cannot edit task details")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aMemberMayChangeTheColumnThroughTheUpdateEndpoint() {
        TaskResponse task = createTask();
        UUID inProgress = board.columns().get(1).id();

        TaskResponse updated = commands.update(member, task.id(),
                new UpdateTaskRequest(null, null, null, null, null, inProgress));

        assertThat(updated.columnId()).isEqualTo(inProgress);
    }

    @Test
    void movingIntoTheTerminalColumnCompletesTheTaskAndQueuesAnEvent() {
        TaskResponse task = createTask();
        outbox.deleteAll();                       // ignore task.assigned
        UUID completed = board.columns().get(2).id();

        TaskResponse moved = commands.move(admin, task.id(),
                new MoveTaskRequest(completed, null));

        assertThat(moved.completedAt()).isNotNull();
        assertThat(outbox.findAll())
                .singleElement()
                .satisfies(e -> assertThat(e.getEventType()).isEqualTo("task.completed"));
    }

    @Test
    void movingBackOutOfTheTerminalColumnClearsCompletion() {
        TaskResponse task = createTask();
        commands.move(admin, task.id(),
                new MoveTaskRequest(board.columns().get(2).id(), null));

        TaskResponse reopened = commands.move(admin, task.id(),
                new MoveTaskRequest(board.columns().get(0).id(), null));

        assertThat(reopened.completedAt()).isNull();
    }

    @Test
    void membersCannotCreateTasks() {
        assertThatThrownBy(() -> commands.create(member, board.id(),
                new CreateTaskRequest("Sneaky task", "", memberId, Severity.LOW,
                        null, board.columns().get(0).id())))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
