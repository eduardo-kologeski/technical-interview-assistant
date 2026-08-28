package com.kologeski.interviewassistant.auth;

import jakarta.validation.constraints.*;

public record LoginRequest(@NotBlank @Email String email, @NotBlank String password,
                           boolean rememberMe, @Size(max = 120) String deviceName) { }
