package com.kologeski.interviewassistant.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final AccountDeletionService deletion;

    public UserController(AccountDeletionService deletion) { this.deletion = deletion; }

    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        User user = principal.user();
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteMe(@Valid @RequestBody DeleteAccountCommand command) { deletion.delete(command.password()); }

    public record UserResponse(Long id, String name, String email) { }
    public record DeleteAccountCommand(@NotBlank @Size(min = 8, max = 72) String password) { }
}
