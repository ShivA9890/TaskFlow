package dev.taskflow.identity.web;

import dev.taskflow.identity.security.CurrentUser;
import dev.taskflow.identity.service.TeamService;
import dev.taskflow.identity.web.Dtos.AddTeamMemberRequest;
import dev.taskflow.identity.web.Dtos.CreateTeamRequest;
import dev.taskflow.identity.web.Dtos.TeamResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping
    public List<TeamResponse> list() {
        return teamService.list(CurrentUser.require());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeamResponse create(@Valid @RequestBody CreateTeamRequest request) {
        var claims = CurrentUser.requireAdmin("Only admins can create teams.");
        return teamService.create(claims, request);
    }

    @PostMapping("/{id}/members")
    public TeamResponse addMember(@PathVariable UUID id,
                                  @Valid @RequestBody AddTeamMemberRequest request) {
        var claims = CurrentUser.requireAdmin("Only admins can change team membership.");
        return teamService.addMember(claims, id, request);
    }
}
