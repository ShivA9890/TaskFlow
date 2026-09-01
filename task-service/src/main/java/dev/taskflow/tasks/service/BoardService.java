package dev.taskflow.tasks.service;

import dev.taskflow.tasks.domain.Board;
import dev.taskflow.tasks.domain.BoardColumn;
import dev.taskflow.tasks.repo.BoardColumnRepository;
import dev.taskflow.tasks.repo.BoardRepository;
import dev.taskflow.tasks.repo.TaskRepository;
import dev.taskflow.tasks.security.TokenClaims;
import dev.taskflow.tasks.web.ApiException;
import dev.taskflow.tasks.web.Dtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class BoardService {

    private final BoardRepository boards;
    private final BoardColumnRepository columns;
    private final TaskRepository tasks;

    public BoardService(BoardRepository boards, BoardColumnRepository columns,
                        TaskRepository tasks) {
        this.boards = boards;
        this.columns = columns;
        this.tasks = tasks;
    }

    @Transactional(readOnly = true)
    public List<BoardResponse> list(TokenClaims claims) {
        return boards.findAllByOrgIdOrderByNameAsc(claims.orgId())
                .stream()
                .map(BoardResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public BoardDetailResponse detail(TokenClaims claims, UUID boardId) {
        Board board = requireBoard(claims, boardId);
        return new BoardDetailResponse(
                board.getId(),
                board.getName(),
                board.getTeamId(),
                columns.findAllByBoardIdOrderByPositionAsc(board.getId())
                        .stream().map(ColumnResponse::from).toList(),
                tasks.findAllByBoardIdOrderByPositionAsc(board.getId())
                        .stream().map(TaskResponse::from).toList());
    }

    @Transactional
    public BoardResponse create(TokenClaims claims, CreateBoardRequest request) {
        Board board = boards.save(Board.create(
                claims.orgId(), request.teamId(), request.name().trim(), claims.userId()));

        List<String> names = request.columns();
        for (int i = 0; i < names.size(); i++) {
            columns.save(BoardColumn.create(
                    board, names.get(i).trim(), i, i == names.size() - 1));
        }
        return BoardResponse.from(board);
    }

    @Transactional
    public BoardResponse update(TokenClaims claims, UUID boardId, UpdateBoardRequest request) {
        Board board = requireBoard(claims, boardId);
        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw ApiException.unprocessable("The board name cannot be empty.");
            }
            board.setName(request.name().trim());
        }
        if (request.teamId() != null) {
            board.setTeamId(request.teamId());
        }
        return BoardResponse.from(board);
    }

    @Transactional
    public void delete(TokenClaims claims, UUID boardId) {
        boards.delete(requireBoard(claims, boardId));
    }

    @Transactional
    public ColumnResponse addColumn(TokenClaims claims, UUID boardId,
                                    CreateColumnRequest request) {
        Board board = requireBoard(claims, boardId);
        List<BoardColumn> existing = columns.findAllByBoardIdOrderByPositionAsc(boardId);

        if (existing.size() >= 6) {
            throw ApiException.unprocessable("A board can have at most 6 columns.");
        }

        // New columns land just before the terminal one, which always stays last.
        BoardColumn terminal = existing.stream()
                .filter(BoardColumn::isTerminal)
                .findFirst()
                .orElse(null);

        int insertAt = terminal != null ? terminal.getPosition() : existing.size();
        BoardColumn column = BoardColumn.create(board, request.name().trim(), insertAt, false);
        if (terminal != null) {
            terminal.setPosition(insertAt + 1);
        }
        return ColumnResponse.from(columns.save(column));
    }

    @Transactional
    public void deleteColumn(TokenClaims claims, UUID columnId) {
        BoardColumn column = columns.findById(columnId)
                .orElseThrow(() -> ApiException.notFound("That column no longer exists."));
        requireBoard(claims, column.getBoard().getId());

        if (columns.countByBoardId(column.getBoard().getId()) <= 3) {
            throw ApiException.unprocessable("A board needs at least 3 columns.");
        }
        if (column.isTerminal()) {
            throw ApiException.unprocessable(
                    "The completed column cannot be removed. Rename it instead.");
        }
        if (tasks.countByColumnId(columnId) > 0) {
            throw ApiException.unprocessable(
                    "Move the tasks out of this column before deleting it.");
        }

        int removed = column.getPosition();
        columns.delete(column);
        columns.flush();

        columns.findAllByBoardIdOrderByPositionAsc(column.getBoard().getId())
                .stream()
                .filter(c -> c.getPosition() > removed)
                .forEach(c -> c.setPosition(c.getPosition() - 1));
    }

    /** Tenancy boundary: a board in another org reads as missing, not forbidden. */
    Board requireBoard(TokenClaims claims, UUID boardId) {
        return boards.findByIdAndOrgId(boardId, claims.orgId())
                .orElseThrow(() -> ApiException.notFound("That board no longer exists."));
    }
}
