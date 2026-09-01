package dev.taskflow.identity.web;

import dev.taskflow.identity.security.CurrentUser;
import dev.taskflow.identity.service.InviteService;
import dev.taskflow.identity.web.Dtos.AcceptInviteRequest;
import dev.taskflow.identity.web.Dtos.AuthResponse;
import dev.taskflow.identity.web.Dtos.CreateInviteRequest;
import dev.taskflow.identity.web.Dtos.InviteResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/invites")
public class InviteController {

    private final InviteService inviteService;

    public InviteController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    @GetMapping
    public List<InviteResponse> list() {
        return inviteService.list(CurrentUser.requireAdmin("Only admins can view invites."));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InviteResponse create(@Valid @RequestBody CreateInviteRequest request) {
        var claims = CurrentUser.requireAdmin("Only admins can invite members.");
        return inviteService.create(claims, request);
    }

    @PostMapping("/accept")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse accept(@Valid @RequestBody AcceptInviteRequest request) {
        return inviteService.accept(request);
    }
}
