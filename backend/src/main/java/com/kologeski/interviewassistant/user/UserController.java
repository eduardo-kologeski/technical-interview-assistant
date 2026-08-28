package com.kologeski.interviewassistant.user;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        User user = principal.user();
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }

    public record UserResponse(Long id, String name, String email) { }
}
