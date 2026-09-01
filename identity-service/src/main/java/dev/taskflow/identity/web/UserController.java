package dev.taskflow.identity.web;

import dev.taskflow.identity.security.CurrentUser;
import dev.taskflow.identity.service.UserService;
import dev.taskflow.identity.web.Dtos.UpdateMeRequest;
import dev.taskflow.identity.web.Dtos.UpdateUserRequest;
import dev.taskflow.identity.web.Dtos.UserResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse me() {
        return userService.me(CurrentUser.require());
    }

    @PatchMapping("/me")
    public UserResponse updateMe(@Valid @RequestBody UpdateMeRequest request) {
        return userService.updateMe(CurrentUser.require(), request);
    }

    @GetMapping("/users")
    public List<UserResponse> listUsers() {
        return userService.listInOrg(CurrentUser.require());
    }

    @PatchMapping("/users/{id}")
    public UserResponse updateUser(@PathVariable UUID id,
                                   @Valid @RequestBody UpdateUserRequest request) {
        var claims = CurrentUser.requireAdmin("Only admins can change member access.");
        return userService.updateAsAdmin(claims, id, request);
    }
}
