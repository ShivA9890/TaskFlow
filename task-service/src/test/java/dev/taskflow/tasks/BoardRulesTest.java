package dev.taskflow.tasks;

import dev.taskflow.tasks.repo.BoardColumnRepository;
import dev.taskflow.tasks.repo.BoardRepository;
import dev.taskflow.tasks.repo.OutboxEventRepository;
import dev.taskflow.tasks.repo.TaskRepository;
import dev.taskflow.tasks.security.TokenClaims;
import dev.taskflow.tasks.service.BoardService;
import dev.taskflow.tasks.web.ApiException;
import dev.taskflow.tasks.web.Dtos.BoardDetailResponse;
import dev.taskflow.tasks.web.Dtos.BoardResponse;
import dev.taskflow.tasks.web.Dtos.CreateBoardRequest;
import dev.taskflow.tasks.web.Dtos.CreateColumnRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.orm.jpa.JpaSystemException;

import org.springframework.orm.jpa.JpaSystemException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoardRulesTest extends IntegrationTest {

    @Autowired BoardService boardService;
    @Autowired BoardRepository boards;
    @Autowired BoardColumnRepository columns;
    @Autowired TaskRepository tasks;
    @Autowired OutboxEventRepository outbox;

    private TokenClaims admin;

    @BeforeEach
    void setUp() {
        outbox.deleteAll();
        tasks.deleteAll();
        boards.deleteAll();          // cascades to board_columns
        admin = new TokenClaims(UUID.randomUUID(), UUID.randomUUID(), "ADMIN", List.of());
    }

    private BoardResponse createBoard(String... columnNames) {
        return boardService.create(admin,
                new CreateBoardRequest("Platform", null, List.of(columnNames)));
    }

     @Test
    void aBoardWithFewerThanThreeColumnsIsRejectedByTheDatabase() {
        // The trigger is deferred, so it fires at COMMIT rather than on the insert.
        // Spring surfaces that as JpaSystemException; ApiExceptionHandler maps it to
        // a 422 for callers coming through HTTP.
        assertThatThrownBy(() -> createBoard("To do", "Done"))
                .isInstanceOf(JpaSystemException.class)
                .hasStackTraceContaining("A board needs at least 3 columns.");
    }

     @Test
    void aBoardWithMoreThanSixColumnsIsRejectedByTheDatabase() {
        assertThatThrownBy(() ->
                createBoard("C1", "C2", "C3", "C4", "C5", "C6", "C7"))
                .isInstanceOf(JpaSystemException.class)
                .hasStackTraceContaining("at most 6 columns");
    }
    @Test
    void aSeventhColumnCannotBeAddedToAFullBoard() {
        BoardResponse board = createBoard("C1", "C2", "C3", "C4", "C5", "C6");

        assertThatThrownBy(() -> boardService.addColumn(
                admin, board.id(), new CreateColumnRequest("Blocked")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("at most 6 columns");
    }

    @Test
    void theLastColumnIsTerminalAndTheOthersAreNot() {
        BoardResponse board = createBoard("To do", "In progress", "Completed");
        BoardDetailResponse detail = boardService.detail(admin, board.id());

        assertThat(detail.columns()).hasSize(3);
        assertThat(detail.columns().get(0).isTerminal()).isFalse();
        assertThat(detail.columns().get(1).isTerminal()).isFalse();
        assertThat(detail.columns().get(2).isTerminal()).isTrue();
    }

    @Test
    void aNewColumnLandsBeforeTheTerminalColumn() {
        BoardResponse board = createBoard("To do", "In progress", "Completed");

        boardService.addColumn(admin, board.id(), new CreateColumnRequest("In review"));
        BoardDetailResponse detail = boardService.detail(admin, board.id());

        assertThat(detail.columns()).extracting("name")
                .containsExactly("To do", "In progress", "In review", "Completed");
        assertThat(detail.columns().get(3).isTerminal()).isTrue();
    }

    @Test
    void aBoardInAnotherOrgReadsAsMissingRatherThanForbidden() {
        BoardResponse board = createBoard("To do", "In progress", "Completed");
        TokenClaims otherOrg = new TokenClaims(
                UUID.randomUUID(), UUID.randomUUID(), "ADMIN", List.of());

        // 404 rather than 403 on purpose: 403 would confirm the board exists.
        assertThatThrownBy(() -> boardService.detail(otherOrg, board.id()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
