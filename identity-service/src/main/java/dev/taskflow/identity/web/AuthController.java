package dev.taskflow.identity.web;

import dev.taskflow.identity.security.CurrentUser;
import dev.taskflow.identity.security.JwtService;
import dev.taskflow.identity.service.AuthService;
import dev.taskflow.identity.web.Dtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register-org")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse registerOrg(@Valid @RequestBody RegisterOrgRequest request) {
        return authService.registerOrg(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        authService.logout(CurrentUser.require());
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public String jwks() {
        return jwtService.jwksJson();
    }
}
