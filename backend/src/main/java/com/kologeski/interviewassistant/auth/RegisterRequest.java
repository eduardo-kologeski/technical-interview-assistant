package com.kologeski.interviewassistant.auth;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 180) String email,
        @NotBlank @Size(min = 6, max = 72) String password,
        Boolean rememberMe,
        @Size(max = 120) String deviceName) { }
