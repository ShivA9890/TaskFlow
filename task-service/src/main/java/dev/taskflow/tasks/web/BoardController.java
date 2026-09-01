package dev.taskflow.tasks.web;

import dev.taskflow.tasks.security.CurrentUser;
import dev.taskflow.tasks.service.BoardService;
import dev.taskflow.tasks.web.Dtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class BoardController {

    private final BoardService boardService;

    public BoardController(BoardService boardService) {
        this.boardService = boardService;
    }

    @GetMapping("/boards")
    public List<BoardResponse> list() {
        return boardService.list(CurrentUser.require());
    }

    @GetMapping("/boards/{id}")
    public BoardDetailResponse detail(@PathVariable UUID id) {
        return boardService.detail(CurrentUser.require(), id);
    }

    @PostMapping("/boards")
    @ResponseStatus(HttpStatus.CREATED)
    public BoardResponse create(@Valid @RequestBody CreateBoardRequest request) {
        var claims = CurrentUser.requireAdmin("Only admins can create boards.");
        return boardService.create(claims, request);
    }

    @PatchMapping("/boards/{id}")
    public BoardResponse update(@PathVariable UUID id,
                                @Valid @RequestBody UpdateBoardRequest request) {
        var claims = CurrentUser.requireAdmin("Only admins can change boards.");
        return boardService.update(claims, id, request);
    }

    @DeleteMapping("/boards/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        boardService.delete(CurrentUser.requireAdmin("Only admins can delete boards."), id);
    }

    @PostMapping("/boards/{id}/columns")
    @ResponseStatus(HttpStatus.CREATED)
    public ColumnResponse addColumn(@PathVariable UUID id,
                                    @Valid @RequestBody CreateColumnRequest request) {
        var claims = CurrentUser.requireAdmin("Only admins can change columns.");
        return boardService.addColumn(claims, id, request);
    }

    @DeleteMapping("/columns/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteColumn(@PathVariable UUID id) {
        boardService.deleteColumn(
                CurrentUser.requireAdmin("Only admins can change columns."), id);
    }
}
